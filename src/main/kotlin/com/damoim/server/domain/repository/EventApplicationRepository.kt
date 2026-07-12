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

    fun findByEventIdAndUserId(eventId: Long, userId: Long): EventApplication?

    /** 47/24 신청자 목록 — 취소 포함 전체(신청순). */
    fun findByEventIdOrderByCreatedAtAsc(eventId: Long): List<EventApplication>

    fun countByEventIdAndStatus(eventId: Long, status: ApplicantStatus): Long

    /** 목록 신청수 배치 — [eventId, count] (APPLIED만). */
    @Query(
        "select ea.eventId, count(ea) from EventApplication ea " +
            "where ea.eventId in :eventIds and ea.status = :status group by ea.eventId",
    )
    fun countByEventsAndStatus(
        @Param("eventIds") eventIds: Collection<Long>,
        @Param("status") status: ApplicantStatus,
    ): List<Array<Any>>

    /** 목록 appliedByMe 배치 — 내가 신청(APPLIED)한 event id들. */
    @Query(
        "select ea.eventId from EventApplication ea " +
            "where ea.userId = :userId and ea.eventId in :eventIds and ea.status = :status",
    )
    fun findAppliedEventIds(
        @Param("userId") userId: Long,
        @Param("eventIds") eventIds: Collection<Long>,
        @Param("status") status: ApplicantStatus,
    ): List<Long>

    /** 48 내 신청 내역 — 활성 동아리의 APPLIED 신청(일정 날짜순). */
    @Query(
        "select ea from EventApplication ea, Event e, Schedule s " +
            "where ea.eventId = e.id and e.scheduleId = s.id " +
            "and ea.userId = :userId and s.clubId = :clubId and ea.status = :status " +
            "order by s.scheduleDate desc",
    )
    fun findMyAppliedInClub(
        @Param("userId") userId: Long,
        @Param("clubId") clubId: Long,
        @Param("status") status: ApplicantStatus,
    ): List<EventApplication>
}
