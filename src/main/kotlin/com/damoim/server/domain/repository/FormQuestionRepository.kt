package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.FormQuestion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FormQuestionRepository : JpaRepository<FormQuestion, Long> {
    fun findByEventIdOrderByPositionAsc(eventId: Long): List<FormQuestion>

    /**
     * 벌크 DELETE(즉시 실행) — 폼 교체 시 (event_id,position) 유니크 충돌(플러시 순서) 방지.
     * DB FK ON DELETE CASCADE로 event_answers도 함께 정리된다.
     */
    @Modifying
    @Query("delete from FormQuestion fq where fq.eventId = :eventId")
    fun deleteByEventId(@Param("eventId") eventId: Long)
}
