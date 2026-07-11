package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    fun findByTokenHash(tokenHash: String): RefreshToken?

    /**
     * 활성 상태일 때만 원자적으로 폐기. 회전 시 사용 — 반환값 1이면 이 요청이 회전 획득,
     * 0이면 다른 요청이 이미 회전(동시 refresh) 또는 이미 폐기됨 → 재사용으로 처리.
     */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
    fun revokeIfActive(@Param("id") id: Long, @Param("now") now: Instant): Int

    /** 해당 유저의 모든 리프레시 토큰 폐기(재사용 탐지 시 전체 무효화·로그아웃). */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    fun revokeAllByUserId(@Param("userId") userId: Long, @Param("now") now: Instant): Int
}
