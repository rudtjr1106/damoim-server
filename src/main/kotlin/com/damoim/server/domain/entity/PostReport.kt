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

    /** 처리 상태 — 접수 시 PENDING, 운영진이 RESOLVED/REJECTED로 전이(UGC 정책상 조치 기록 필수). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: ReportStatus = ReportStatus.PENDING

    /** 처리 시 취한 조치. 미처리 건은 NULL이라 "조치 없음(NONE)"과 구분된다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = true, length = 20)
    var action: ReportAction? = null

    @Column(name = "handled_by", nullable = true)
    var handledBy: Long? = null

    @Column(name = "handled_at", nullable = true)
    var handledAt: Instant? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
