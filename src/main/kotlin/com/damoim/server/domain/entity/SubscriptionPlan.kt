package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.PlanTier
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "subscription_plans")
class SubscriptionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    var tier: PlanTier = PlanTier.FREE

    @Column(name = "name", nullable = false, length = 50)
    var name: String = ""

    @Column(name = "price_krw", nullable = false)
    var priceKrw: Int = 0

    @Column(name = "member_limit", nullable = false)
    var memberLimit: Int = 0

    @Column(name = "recommended", nullable = false)
    var recommended: Boolean = false

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}
