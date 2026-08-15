package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.DeviceToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DeviceTokenRepository : JpaRepository<DeviceToken, Long> {
    fun findByToken(token: String): DeviceToken?

    fun deleteByToken(token: String)

    /**
     * 51 회원 탈퇴 정리 — 이 유저의 모든 디바이스 토큰 제거.
     * 탈퇴는 소프트(users 행 유지)라 FK CASCADE가 돌지 않고, 서버가 세션을 즉시 폐기해
     * 클라의 해제 요청도 놓칠 수 있다 — 남으면 그 기기가 탈퇴한 계정의 푸시를 계속 받는다.
     */
    fun deleteByUserId(userId: Long)

    /** 팬아웃 발송용 — 수신 userId들의 토큰만 배치 조회(N+1 방지). */
    @Query("select d.token from DeviceToken d where d.userId in :ids")
    fun findTokensByUserIdIn(@Param("ids") ids: Collection<Long>): List<String>

    /** FCM UNREGISTERED/INVALID 응답으로 확인된 죽은 토큰 정리. */
    @Modifying
    @Query("delete from DeviceToken d where d.token in :tokens")
    fun deleteByTokenIn(@Param("tokens") tokens: Collection<String>)
}
