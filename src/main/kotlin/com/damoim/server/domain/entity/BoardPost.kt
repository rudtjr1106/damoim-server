package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.*
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "board_posts")
class BoardPost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "club_id", nullable = false)
    var clubId: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    var category: BoardCategory = BoardCategory.NOTICE

    @Column(name = "title", nullable = false, length = 200)
    var title: String = ""

    @Column(name = "content", nullable = false, columnDefinition = "text")
    var content: String = ""

    @Column(name = "author_id", nullable = true)
    var authorId: Long? = null

    @Column(name = "is_pinned", nullable = false)
    var isPinned: Boolean = false

    @Column(name = "view_count", nullable = false)
    var viewCount: Int = 0

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null

    @Column(name = "deleted_at", nullable = true)
    var deletedAt: Instant? = null
}
