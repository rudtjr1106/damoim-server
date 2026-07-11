package com.damoim.server.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "post_reads")
class PostRead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "post_id", nullable = false)
    var postId: Long = 0

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @CreationTimestamp
    @Column(name = "read_at", nullable = false, updatable = false)
    var readAt: Instant? = null
}
