package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.NotificationTargetType
import com.damoim.server.domain.enums.NotificationType
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "notifications")
class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "club_id", nullable = true)
    var clubId: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var type: NotificationType = NotificationType.JOIN_APPROVED

    @Column(name = "text", nullable = false, length = 500)
    var text: String = ""

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false

    /** 이동 대상 종류 — NULL이면 이동 없음(가입 승인 등). */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = true, length = 20)
    var targetType: NotificationTargetType? = null

    /** 이동 대상 id — targetType과 항상 동반(DB CHECK). */
    @Column(name = "target_id", nullable = true)
    var targetId: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
