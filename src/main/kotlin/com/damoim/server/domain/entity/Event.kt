package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.*
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "events")
class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "schedule_id", nullable = false)
    var scheduleId: Long = 0

    @Column(name = "capacity", nullable = false)
    var capacity: Int = 0

    @Column(name = "deadline_at", nullable = false)
    var deadlineAt: Instant = Instant.EPOCH

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: EventStatus = EventStatus.OPEN

    @Column(name = "meta", nullable = true, length = 200)
    var meta: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}
