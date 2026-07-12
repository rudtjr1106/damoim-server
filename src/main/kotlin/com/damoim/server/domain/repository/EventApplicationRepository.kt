package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.EventApplication
import com.damoim.server.domain.enums.ApplicantStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EventApplicationRepository : JpaRepository<EventApplication, Long> {

    /**
     * 회원 상세(18) 이벤트 참여 수 — 동아리 스코프 집계.
     * event_applications → events → schedules 로 조인해 해당 동아리의 이벤트만 카운트.
     * (F 그룹 도입 전에는 event_applications가 비어 0을 반환한다.)
     */
    @Query(
        "select count(ea) from EventApplication ea, Event e, Schedule s " +
            "where ea.eventId = e.id and e.scheduleId = s.id " +
            "and ea.userId = :userId and s.clubId = :clubId and ea.status = :status",
    )
    fun countAppliedInClub(
        @Param("userId") userId: Long,
        @Param("clubId") clubId: Long,
        @Param("status") status: ApplicantStatus,
    ): Long
}
