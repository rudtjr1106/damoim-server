package com.damoim.server.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "blocked_users")
class BlockedUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "club_id", nullable = false)
    var clubId: Long = 0

    @Column(name = "blocked_user_id", nullable = false)
    var blockedUserId: Long = 0

    @Column(name = "is_withdrawn", nullable = false)
    var isWithdrawn: Boolean = false

    @Column(name = "blocked_at", nullable = false)
    var blockedAt: Instant = Instant.EPOCH

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
