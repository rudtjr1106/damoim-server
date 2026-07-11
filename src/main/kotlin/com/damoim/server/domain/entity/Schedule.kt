package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.*
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "schedules")
class Schedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "club_id", nullable = false)
    var clubId: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    var type: ScheduleType = ScheduleType.SCHEDULE

    @Column(name = "title", nullable = false, length = 200)
    var title: String = ""

    @Column(name = "schedule_date", nullable = false)
    var scheduleDate: LocalDate = LocalDate.EPOCH

    @Column(name = "start_hour", nullable = false)
    var startHour: Short = 0

    @Column(name = "start_minute", nullable = false)
    var startMinute: Short = 0

    @Column(name = "end_date", nullable = true)
    var endDate: LocalDate? = null

    @Column(name = "end_hour", nullable = true)
    var endHour: Short? = null

    @Column(name = "end_minute", nullable = true)
    var endMinute: Short? = null

    @Column(name = "location", nullable = false, length = 200)
    var location: String = ""

    @Column(name = "memo", nullable = false, columnDefinition = "text")
    var memo: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "accent", nullable = false, length = 16)
    var accent: ScheduleAccent = ScheduleAccent.PRIMARY

    @Column(name = "host_user_id", nullable = true)
    var hostUserId: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}
