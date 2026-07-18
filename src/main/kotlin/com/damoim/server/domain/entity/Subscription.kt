package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.PlanTier
import com.damoim.server.domain.enums.SubscriptionStatus
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "subscriptions")
class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "club_id", nullable = false)
    var clubId: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    var tier: PlanTier = PlanTier.FREE

    @Column(name = "member_limit", nullable = false)
    var memberLimit: Int = 0

    @Column(name = "storage_quota_bytes", nullable = false)
    var storageQuotaBytes: Long = 1_073_741_824   // 계약 시점 플랜 저장 용량 스냅샷(41)

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: SubscriptionStatus = SubscriptionStatus.ACTIVE

    @Column(name = "started_at", nullable = true)
    var startedAt: Instant? = null

    @Column(name = "next_billing_at", nullable = true)
    var nextBillingAt: Instant? = null

    @Column(name = "canceled_at", nullable = true)
    var canceledAt: Instant? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}
