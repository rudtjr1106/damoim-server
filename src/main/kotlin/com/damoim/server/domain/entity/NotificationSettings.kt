package com.damoim.server.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.time.LocalTime

@Entity
@Table(name = "notification_settings")
class NotificationSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "push", nullable = false)
    var push: Boolean = true

    @Column(name = "new_post", nullable = false)
    var newPost: Boolean = true

    @Column(name = "comment", nullable = false)
    var comment: Boolean = true

    @Column(name = "schedule_reminder", nullable = false)
    var scheduleReminder: Boolean = true

    @Column(name = "join_request", nullable = false)
    var joinRequest: Boolean = true

    @Column(name = "event_apply", nullable = false)
    var eventApply: Boolean = true

    @Column(name = "reminder_days_before", nullable = true)
    var reminderDaysBefore: Int? = null

    @Column(name = "reminder_hours_before", nullable = true)
    var reminderHoursBefore: Int? = null

    @Column(name = "dnd_enabled", nullable = false)
    var dndEnabled: Boolean = false

    @Column(name = "dnd_start", nullable = true)
    var dndStart: LocalTime? = null

    @Column(name = "dnd_end", nullable = true)
    var dndEnd: LocalTime? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}
