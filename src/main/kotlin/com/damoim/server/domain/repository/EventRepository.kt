package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.Event
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EventRepository : JpaRepository<Event, Long> {
    fun findByScheduleId(scheduleId: Long): Event?
    fun findByScheduleIdIn(scheduleIds: Collection<Long>): List<Event>

    /** 신청 정원 경쟁 직렬화 — 이벤트 행 비관적 락(SELECT … FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Event?
}
