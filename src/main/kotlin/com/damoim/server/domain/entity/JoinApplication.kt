package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.*
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "join_applications")
class JoinApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "club_id", nullable = false)
    var clubId: Long = 0

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "desired_cohort_id", nullable = true)
    var desiredCohortId: Long? = null

    @Column(name = "message", nullable = true, columnDefinition = "text")
    var message: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: JoinStatus = JoinStatus.PENDING

    @Column(name = "rejection_reason", nullable = true, columnDefinition = "text")
    var rejectionReason: String? = null

    @Column(name = "decided_at", nullable = true)
    var decidedAt: Instant? = null

    @Column(name = "decided_by", nullable = true)
    var decidedBy: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}
