package com.damoim.server.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "poll_votes")
class PollVote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "poll_id", nullable = false)
    var pollId: Long = 0

    @Column(name = "poll_option_id", nullable = false)
    var pollOptionId: Long = 0

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
