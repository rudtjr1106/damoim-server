package com.damoim.server.club

import com.damoim.server.common.ConflictException
import com.damoim.server.common.NotFoundException
import com.damoim.server.common.TimeLabels
import com.damoim.server.domain.entity.ClubMember
import com.damoim.server.domain.entity.JoinApplication
import com.damoim.server.domain.entity.Notification
import com.damoim.server.domain.enums.JoinStatus
import com.damoim.server.domain.enums.MemberRole
import com.damoim.server.domain.enums.MemberStatus
import com.damoim.server.domain.enums.NotificationType
import com.damoim.server.domain.repository.ClubMemberRepository
import com.damoim.server.domain.repository.ClubRepository
import com.damoim.server.domain.repository.CohortRepository
import com.damoim.server.domain.repository.JoinApplicationRepository
import com.damoim.server.domain.repository.NotificationRepository
import com.damoim.server.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class JoinService(
    private val clubRepository: ClubRepository,
    private val clubMemberRepository: ClubMemberRepository,
    private val cohortRepository: CohortRepository,
    private val joinApplicationRepository: JoinApplicationRepository,
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
    private val membership: MembershipService,
) {
    /** 가입 코드 제출(03) — 활성 코드로 동아리 조회 → 대기 신청 생성(멱등). */
    @Transactional
    fun submitCode(userId: Long, code: String): JoinResultResponse {
        val club = clubRepository.findByJoinCodeAndJoinCodeActiveIsTrue(code.trim())
            ?: throw NotFoundException("유효하지 않은 가입 코드입니다.", "INVALID_JOIN_CODE")
        if (clubMemberRepository.existsByClubIdAndUserId(club.id, userId)) {
            throw ConflictException("이미 가입된 동아리입니다.", "ALREADY_MEMBER")
        }
        val existing = joinApplicationRepository.findByClubIdAndUserIdAndStatus(club.id, userId, JoinStatus.PENDING)
        if (existing == null) {
            joinApplicationRepository.save(
                JoinApplication().apply {
                    clubId = club.id
                    this.userId = userId
                    status = JoinStatus.PENDING
                },
            )
        }
        return JoinResultResponse(ClubSummary.from(club), JoinStatus.PENDING.name)
    }

    /** 가입 신청 관리(09) — LEADER만. */
    @Transactional(readOnly = true)
    fun applicants(userId: Long): ApplicantsBoardResponse {
        val clubId = membership.requireLeader(userId).clubId
        val pending = joinApplicationRepository.findByClubIdAndStatusOrderByCreatedAtDesc(clubId, JoinStatus.PENDING)
        val processed = joinApplicationRepository.findByClubIdAndStatusInOrderByCreatedAtDesc(
            clubId,
            listOf(JoinStatus.APPROVED, JoinStatus.REJECTED),
        )
        val all = pending + processed
        // 배치 조회로 N+1 제거(이름·희망기수)
        val userNames = userRepository.findAllById(all.map { it.userId }).associate { it.id to it.nickname }
        val cohortShorts = cohortRepository
            .findAllById(all.mapNotNull { it.desiredCohortId }.distinct())
            .associate { it.id to it.short }

        return ApplicantsBoardResponse(
            pending = pending.map { toApplicant(it, userNames, cohortShorts) },
            processed = processed.map {
                ProcessedApplicantResponse(
                    applicant = toApplicant(it, userNames, cohortShorts),
                    approved = it.status == JoinStatus.APPROVED,
                    decidedLabel = it.decidedAt?.let { d -> TimeLabels.ago(d) } ?: "",
                )
            },
        )
    }

    /** 승인/거절(09) — LEADER만. IDOR 방지: 신청이 리더의 동아리 소속인지 확인. */
    @Transactional
    fun decide(userId: Long, applicationId: Long, req: DecideRequest) {
        val clubId = membership.requireLeader(userId).clubId
        val app = joinApplicationRepository.findById(applicationId)
            .orElseThrow { NotFoundException("신청을 찾을 수 없습니다.") }
        if (app.clubId != clubId) {
            // 다른 동아리의 신청 — 존재 노출 없이 차단
            throw NotFoundException("신청을 찾을 수 없습니다.")
        }
        if (app.status != JoinStatus.PENDING) {
            throw ConflictException("이미 처리된 신청입니다.", "ALREADY_DECIDED")
        }
        val now = Instant.now()
        app.decidedAt = now
        app.decidedBy = userId

        if (req.approve) {
            app.status = JoinStatus.APPROVED
            joinApplicationRepository.save(app)
            if (!clubMemberRepository.existsByClubIdAndUserId(clubId, app.userId)) {
                clubMemberRepository.save(
                    ClubMember().apply {
                        this.clubId = clubId
                        this.userId = app.userId
                        memberRole = MemberRole.MEMBER
                        status = MemberStatus.ACTIVE
                        cohortId = app.desiredCohortId
                        joinedAt = now
                    },
                )
            }
            // 신규 회원의 활성 동아리 미설정 시 지정
            userRepository.findById(app.userId).ifPresent { u ->
                if (u.activeClubId == null) {
                    u.activeClubId = clubId
                    userRepository.save(u)
                }
            }
            val clubName = clubRepository.findById(clubId).map { it.name }.orElse("동아리")
            notificationRepository.save(
                Notification().apply {
                    this.userId = app.userId
                    this.clubId = clubId
                    type = NotificationType.JOIN_APPROVED
                    text = "'$clubName' 가입이 승인되었어요"
                    isRead = false
                },
            )
        } else {
            app.status = JoinStatus.REJECTED
            // rejection_reason NOT NULL(CHECK) — 미입력 시 기본 문구
            app.rejectionReason = req.rejectionReason?.trim()?.takeIf { it.isNotEmpty() }
                ?: "가입 신청이 반려되었습니다."
            joinApplicationRepository.save(app)
        }
    }

    private fun toApplicant(
        app: JoinApplication,
        userNames: Map<Long, String>,
        cohortShorts: Map<Long, String>,
    ): ApplicantResponse {
        val name = userNames[app.userId] ?: "탈퇴한 사용자"
        val desiredGisu = app.desiredCohortId?.let { cohortShorts[it] }
        val created = app.createdAt ?: Instant.now()
        return ApplicantResponse(
            id = app.id,
            name = name,
            initial = name.take(1),
            desiredGisu = desiredGisu,
            appliedDate = TimeLabels.monthDay(created),
            timeAgo = TimeLabels.ago(created),
            message = app.message,
        )
    }
}
