package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.MyCalendarEntry
import org.springframework.data.jpa.repository.JpaRepository

interface MyCalendarEntryRepository : JpaRepository<MyCalendarEntry, Long> {
    fun existsByUserIdAndScheduleId(userId: Long, scheduleId: Long): Boolean
    fun deleteByUserIdAndScheduleId(userId: Long, scheduleId: Long)

    /** 목록 addedToMyCalendar 배치 파생 — 내가 담은 일정 id들. */
    fun findByUserIdAndScheduleIdIn(userId: Long, scheduleIds: Collection<Long>): List<MyCalendarEntry>
}
