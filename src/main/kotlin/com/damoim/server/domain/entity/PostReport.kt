package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.*
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "post_reports")
class PostReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "post_id", nullable = true)
    var postId: Long? = null

    @Column(name = "comment_id", nullable = true)
    var commentId: Long? = null

    @Column(name = "reporter_id", nullable = false)
    var reporterId: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 16)
    var reason: ReportReason = ReportReason.SPAM

    @Column(name = "detail", nullable = true, columnDefinition = "text")
    var detail: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
