package com.damoim.server.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "polls")
class Poll {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "post_id", nullable = false)
    var postId: Long = 0

    @Column(name = "anonymous", nullable = false)
    var anonymous: Boolean = false

    @Column(name = "multi_select", nullable = false)
    var multiSelect: Boolean = false

    @Column(name = "deadline", nullable = true)
    var deadline: Instant? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
