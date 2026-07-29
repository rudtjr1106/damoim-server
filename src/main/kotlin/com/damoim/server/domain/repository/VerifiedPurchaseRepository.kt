package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.VerifiedPurchase
import org.springframework.data.jpa.repository.JpaRepository

interface VerifiedPurchaseRepository : JpaRepository<VerifiedPurchase, Long> {
    /** 이미 소비된 영수증인지(재사용 차단). 최종 방어선은 ux_verified_purchases_platform_txn 유니크 인덱스. */
    fun existsByPlatformAndTransactionId(platform: String, transactionId: String): Boolean
}
