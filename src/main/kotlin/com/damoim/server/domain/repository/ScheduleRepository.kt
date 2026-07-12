package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.Schedule
import org.springframework.data.jpa.repository.JpaRepository

interface ScheduleRepository : JpaRepository<Schedule, Long> {
    /** 21/22 목록 — 날짜 오름차순, 동일 날짜 내 생성순. */
    fun findByClubIdOrderByScheduleDateAscCreatedAtAsc(clubId: Long): List<Schedule>
}
