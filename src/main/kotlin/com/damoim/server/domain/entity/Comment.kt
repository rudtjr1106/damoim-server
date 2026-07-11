package com.damoim.server.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "comments")
class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "post_id", nullable = false)
    var postId: Long = 0

    @Column(name = "author_id", nullable = true)
    var authorId: Long? = null

    @Column(name = "parent_id", nullable = true)
    var parentId: Long? = null

    @Column(name = "content", nullable = false, columnDefinition = "text")
    var content: String = ""

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null

    @Column(name = "deleted_at", nullable = true)
    var deletedAt: Instant? = null
}
