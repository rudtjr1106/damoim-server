package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.EventAnswer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EventAnswerRepository : JpaRepository<EventAnswer, Long> {
    /** 벌크 DELETE(즉시 실행) — delete→insert 재저장 시 유니크 충돌(플러시 순서) 방지. */
    @Modifying
    @Query("delete from EventAnswer ea where ea.applicationId = :applicationId")
    fun deleteByApplicationId(@Param("applicationId") applicationId: Long)

    /**
     * 신청 여러 건의 문항별 응답을 배치 조회(N+1 방지). 문항 텍스트로 조인해 복원.
     * 반환 행: [applicationId, questionText, answer, position]. position 순 정렬.
     */
    @Query(
        "select ea.applicationId, fq.text, ea.answer, fq.position " +
            "from EventAnswer ea, FormQuestion fq " +
            "where ea.questionId = fq.id and ea.applicationId in :appIds " +
            "order by ea.applicationId, fq.position",
    )
    fun findAnswerRows(@Param("appIds") appIds: Collection<Long>): List<Array<Any>>
}
