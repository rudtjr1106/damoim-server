package com.damoim.server.settings

import com.damoim.server.billing.PurchaseVerifier
import com.damoim.server.club.MembershipService
import com.damoim.server.common.BadRequestException
import com.damoim.server.common.NotFoundException
import com.damoim.server.common.TimeLabels
import com.damoim.server.domain.entity.PaymentRecord
import com.damoim.server.domain.entity.Subscription
import com.damoim.server.domain.enums.MemberRole
import com.damoim.server.domain.enums.MemberStatus
import com.damoim.server.domain.enums.PlanTier
import com.damoim.server.domain.enums.SubscriptionStatus
import com.damoim.server.domain.repository.ClubMemberRepository
import com.damoim.server.domain.repository.PaymentRecordRepository
import com.damoim.server.domain.repository.PlanFeatureRepository
import com.damoim.server.domain.repository.SubscriptionPlanRepository
import com.damoim.server.domain.repository.SubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * 구독(27/29/49/50). 조회·결제·해지는 동아리장 전용(빌링은 리더 관리 영역). 플랜 카탈로그는 인증만.
 * ⚠️ 인앱결제 영수증 서버 검증은 미구현(하드닝) — 현재는 클라의 결제 성공 신고를 신뢰.
 */
@Service
class SubscriptionService(
    private val membership: MembershipService,
    private val subscriptionRepository: SubscriptionRepository,
    private val subscriptionPlanRepository: SubscriptionPlanRepository,
    private val planFeatureRepository: PlanFeatureRepository,
    private val paymentRecordRepository: PaymentRecordRepository,
    private val clubMemberRepository: ClubMemberRepository,
    private val purchaseVerifier: PurchaseVerifier,
) {
    /** 상태는 활성 회원 누구나(26 설정홈 표시용). 결제 내역(payments)은 동아리장에게만 노출. */
    @Transactional(readOnly = true)
    fun state(userId: Long): SubscriptionStateResponse {
        val member = membership.currentMembership(userId)
        val isLeader = member.memberRole == MemberRole.LEADER
        return toState(subscriptionRepository.findByClubId(member.clubId), member.clubId, isLeader)
    }

    /** 27 플랜 카탈로그 — 인증된 사용자면 조회 가능(정적 reference data). */
    @Transactional(readOnly = true)
    fun plans(): List<SubscriptionPlanResponse> {
        val plans = subscriptionPlanRepository.findAllByOrderByPriceKrwAsc()
        val features = planFeatureRepository.findByPlanIdInOrderBySortOrderAsc(plans.map { it.id })
            .groupBy { it.planId }
        return plans.map { p ->
            SubscriptionPlanResponse(
                tier = p.tier.name,
                name = p.name,
                priceKrw = p.priceKrw,
                priceLabel = priceLabel(p.priceKrw),
                memberLimitLabel = memberLimitLabel(p.memberLimit),
                features = features[p.id].orEmpty().map { PlanFeatureResponse(it.included, it.text) },
                recommended = p.recommended,
            )
        }
    }

    /** 인앱결제 성공 후 구독 활성화 — 구독 upsert + 결제 내역 기록. LEADER. */
    @Transactional
    fun subscribe(userId: Long, req: SubscribeRequest): SubscriptionStateResponse {
        val clubId = membership.requireLeader(userId).clubId
        val tier = parseTier(req.tier)
        if (tier == PlanTier.FREE) throw BadRequestException("유료 플랜을 선택해주세요.", "INVALID_PLAN")
        val plan = subscriptionPlanRepository.findByTier(tier)
            ?: throw NotFoundException("플랜을 찾을 수 없습니다.")
        // 구독 행 락으로 재구독/동시결제 직렬화
        val current = subscriptionRepository.findByClubIdForUpdate(clubId)
        // 이미 같은 플랜을 이용 중이면 멱등 no-op(더블탭 중복 결제 방지)
        if (current != null && current.tier == tier && current.status == SubscriptionStatus.ACTIVE) {
            return toState(current, clubId, isLeader = true)
        }
        // 결제 증빙 스토어 재검증(fail-closed) — 우회 후 직접 호출 무료 구독 차단. verify-purchases=false면 통과(dev).
        purchaseVerifier.verify(req.platform, req.productId, req.purchaseToken, tier)
        val now = Instant.now()
        val sub = (current ?: Subscription().apply { this.clubId = clubId }).apply {
            this.tier = tier
            memberLimit = plan.memberLimit
            status = SubscriptionStatus.ACTIVE
            startedAt = now
            nextBillingAt = now.plus(Duration.ofDays(BILLING_CYCLE_DAYS))
            canceledAt = null
        }
        subscriptionRepository.save(sub)
        paymentRecordRepository.save(
            PaymentRecord().apply {
                subscriptionId = sub.id
                title = "${plan.name} 플랜 결제"      // 결제 시점 플랜명 스냅샷
                amountKrw = plan.priceKrw
                channel = req.channel.trim().ifEmpty { "App Store" }
                paidAt = now
            },
        )
        return toState(sub, clubId, isLeader = true)
    }

    /**
     * 구독 해지(50) — 즉시 무료 전환이 아니라 '해지 예약'. 갱신일(nextBillingAt)까지 유료 혜택과
     * 결제 내역을 그대로 유지하고, 그 뒤 [toState]의 만료 지연평가로 무료가 된다. LEADER.
     */
    @Transactional
    fun cancel(userId: Long): SubscriptionStateResponse {
        val clubId = membership.requireLeader(userId).clubId
        val sub = subscriptionRepository.findByClubId(clubId)
        if (sub == null || sub.tier == PlanTier.FREE || sub.status == SubscriptionStatus.CANCELED) {
            throw BadRequestException("구독 중이 아니에요.", "NOT_SUBSCRIBED")
        }
        // tier/memberLimit/storageQuota/nextBillingAt 보존 — nextBillingAt이 이제 '이용 만료일'.
        sub.status = SubscriptionStatus.CANCELED
        sub.canceledAt = Instant.now()
        subscriptionRepository.save(sub)
        return toState(sub, clubId, isLeader = true)
    }

    // ── 파생 ──

    /** 해지 예약 후 갱신일이 지났으면 만료(무료 취급). subscribe/cancel/toState/집행이 공유. */
    private fun isExpired(sub: Subscription?): Boolean =
        sub != null && sub.status == SubscriptionStatus.CANCELED &&
            (sub.nextBillingAt == null || !sub.nextBillingAt!!.isAfter(Instant.now()))

    private fun toState(sub: Subscription?, clubId: Long, isLeader: Boolean): SubscriptionStateResponse {
        val memberUsed = clubMemberRepository.countByClubIdAndStatus(clubId, MemberStatus.ACTIVE).toInt()
        if (sub == null || sub.tier == PlanTier.FREE || isExpired(sub)) {
            return SubscriptionStateResponse(
                tier = "FREE",
                planName = "무료 플랜",
                monthlyPriceLabel = priceLabel(0),
                nextBillingLabel = "-",
                memberUsed = memberUsed,
                memberLimit = freeLimit(),
                payments = emptyList(),
                canceled = false,
            )
        }
        val plan = subscriptionPlanRepository.findByTier(sub.tier)
        // 결제 내역은 빌링 민감정보 — 동아리장에게만
        val payments = if (!isLeader) emptyList() else {
            paymentRecordRepository.findBySubscriptionIdOrderByPaidAtDesc(sub.id).map {
                PaymentRecordResponse(
                    title = it.title,
                    dateLabel = TimeLabels.date(it.paidAt),
                    amountLabel = priceLabel(it.amountKrw),
                    channel = it.channel,
                )
            }
        }
        return SubscriptionStateResponse(
            tier = sub.tier.name,
            planName = "${plan?.name ?: sub.tier.name} 플랜",
            monthlyPriceLabel = priceLabel(plan?.priceKrw ?: 0),
            nextBillingLabel = sub.nextBillingAt?.let { TimeLabels.date(it) } ?: "-",
            memberUsed = memberUsed,
            memberLimit = sub.memberLimit,
            payments = payments,
            // 만료 전 해지 예약 상태 = '해지 예정'(갱신일까지 이용 가능)
            canceled = sub.status == SubscriptionStatus.CANCELED,
        )
    }

    private fun freeLimit(): Int = subscriptionPlanRepository.findByTier(PlanTier.FREE)?.memberLimit ?: 30

    private fun memberLimitLabel(limit: Int): String =
        if (limit >= UNLIMITED) "회원 무제한" else "회원 ${limit}명까지"

    private fun priceLabel(krw: Int): String = "₩${"%,d".format(krw)}"

    private fun parseTier(v: String): PlanTier =
        runCatching { PlanTier.valueOf(v.trim().uppercase()) }.getOrElse {
            throw BadRequestException("플랜이 올바르지 않습니다.", "INVALID_PLAN")
        }

    private companion object {
        const val BILLING_CYCLE_DAYS = 30L
        const val UNLIMITED = 9999
    }
}
