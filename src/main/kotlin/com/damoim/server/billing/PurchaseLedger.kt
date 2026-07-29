package com.damoim.server.billing

import com.damoim.server.domain.entity.VerifiedPurchase
import com.damoim.server.domain.enums.PlanTier
import com.damoim.server.domain.repository.VerifiedPurchaseRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

/**
 * 검증에 성공한 스토어 트랜잭션 대장 — "영수증 1건 = 구독 1회"를 보장해
 * 같은 영수증을 여러 동아리에 재사용하는 우회를 막는다. 테스트에서 대체 가능하도록 인터페이스.
 */
interface PurchaseLedger {
    /** 처음 쓰는 트랜잭션이면 기록하고 true, 이미 소비된 트랜잭션이면 false. */
    fun recordFirstUse(platform: String, tier: PlanTier, receipt: VerifiedReceipt): Boolean
}

@Component
class JpaPurchaseLedger(private val repository: VerifiedPurchaseRepository) : PurchaseLedger {

    override fun recordFirstUse(platform: String, tier: PlanTier, receipt: VerifiedReceipt): Boolean {
        if (repository.existsByPlatformAndTransactionId(platform, receipt.transactionId)) return false
        return try {
            // 호출부(SubscriptionService.subscribe)의 트랜잭션 안에서 즉시 INSERT —
            // 구독 활성화가 롤백되면 이 기록도 함께 사라져 영수증이 낭비되지 않는다.
            repository.saveAndFlush(
                VerifiedPurchase().apply {
                    this.platform = platform
                    transactionId = receipt.transactionId
                    productId = receipt.productId
                    this.tier = tier
                },
            )
            true
        } catch (e: DataIntegrityViolationException) {
            // 동시 요청 경합(위 exists 조회를 둘 다 통과) — 유니크 인덱스가 최종 방어선.
            false
        }
    }
}
