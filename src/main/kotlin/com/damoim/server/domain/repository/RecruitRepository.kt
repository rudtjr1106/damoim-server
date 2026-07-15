package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.Recruit
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RecruitRepository : JpaRepository<Recruit, Long> {
    fun findByPostId(postId: Long): Recruit?

    /** 목록/피드용 배치 조회(모집글 카드에 진행률·마감 표시). */
    fun findByPostIdIn(postIds: Collection<Long>): List<Recruit>

    /** 모집 정원 경쟁 직렬화 — count→검사→insert를 비관적 락으로 원자화(F 이벤트 패턴). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Recruit r where r.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Recruit?
}
