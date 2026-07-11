package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.*
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "post_drafts")
class PostDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "club_id", nullable = true)
    var clubId: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    var category: BoardCategory = BoardCategory.NOTICE

    @Column(name = "title", nullable = false, length = 200)
    var title: String = ""

    @Column(name = "content", nullable = false, columnDefinition = "text")
    var content: String = ""

    @Column(name = "pinned", nullable = false)
    var pinned: Boolean = false

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = true, columnDefinition = "jsonb")
    var payload: String? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}
