package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.Club
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ClubRepository : JpaRepository<Club, Long> {
    fun findByJoinCodeAndJoinCodeActiveIsTrue(joinCode: String): Club?
    fun existsByJoinCodeAndJoinCodeActiveIsTrue(joinCode: String): Boolean

    /** 동아리 단위 임계구역 앵커 — 저장공간 쿼터 sum→검사→insert 직렬화(TOCTOU 차단). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Club c where c.id = :clubId")
    fun findByIdForUpdate(@Param("clubId") clubId: Long): Club?
}
