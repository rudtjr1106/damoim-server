package com.damoim.server.auth

import com.damoim.server.domain.repository.RefreshTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 재사용 탐지 시 유저 전 세션을 **별도 트랜잭션(REQUIRES_NEW)**으로 즉시 폐기한다.
 * 호출한 refresh() 트랜잭션이 401 예외로 롤백돼도 이 폐기는 독립 커밋되어
 * '탈취 시 전 세션 강제 로그아웃'이 실제로 반영된다(별도 빈이라 프록시 경계 통과).
 */
@Service
class SessionRevoker(private val refreshTokenRepository: RefreshTokenRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun revokeAllSessions(userId: Long, now: Instant): Int =
        refreshTokenRepository.revokeAllByUserId(userId, now)
}
