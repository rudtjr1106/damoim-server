package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.PlanTier
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

/** 검증 성공한 인앱결제 트랜잭션(V9). (platform, transactionId) 유니크 = 같은 영수증 재사용 차단. */
@Entity
@Table(name = "verified_purchases")
class VerifiedPurchase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "platform", nullable = false, length = 20)
    var platform: String = ""

    @Column(name = "transaction_id", nullable = false, length = 120)
    var transactionId: String = ""

    @Column(name = "product_id", nullable = false, length = 200)
    var productId: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    var tier: PlanTier = PlanTier.FREE

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
