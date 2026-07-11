package com.damoim.server.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "plan_features")
class PlanFeature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "plan_id", nullable = false)
    var planId: Long = 0

    @Column(name = "included", nullable = false)
    var included: Boolean = false

    @Column(name = "text", nullable = false, length = 200)
    var text: String = ""

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
