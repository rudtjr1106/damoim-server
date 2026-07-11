package com.damoim.server.auth

import com.damoim.server.common.ForbiddenException
import com.damoim.server.common.UnauthorizedException
import com.damoim.server.domain.entity.RefreshToken
import com.damoim.server.domain.entity.User
import com.damoim.server.domain.entity.UserOAuthAccount
import com.damoim.server.domain.enums.OAuthProvider
import com.damoim.server.domain.enums.UserStatus
import com.damoim.server.domain.repository.RefreshTokenRepository
import com.damoim.server.domain.repository.UserOAuthAccountRepository
import com.damoim.server.domain.repository.UserRepository
import com.damoim.server.security.JwtProperties
import com.damoim.server.security.JwtTokenProvider
import com.damoim.server.security.RandomTokens
import com.damoim.server.user.UserResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AuthService(
    private val kakaoClient: KakaoClient,
    private val userRepository: UserRepository,
    private val oauthRepository: UserOAuthAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val sessionRevoker: SessionRevoker,
    private val tokenProvider: JwtTokenProvider,
    private val jwtProperties: JwtProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 카카오 access token 서버 검증 → 유저 find-or-create → 토큰 발급. */
    @Transactional
    fun loginWithKakao(kakaoAccessToken: String): TokenResponse {
        val info = kakaoClient.fetchUserInfo(kakaoAccessToken)  // 실패 시 UnauthorizedException
        val account = oauthRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, info.kakaoUserId)
        val user = if (account != null) {
            val existing = userRepository.findById(account.userId).orElseThrow { UnauthorizedException() }
            requireActive(existing)   // 정지·탈퇴 계정은 로그인 거부
            existing
        } else {
            val created = userRepository.save(
                User().apply {
                    nickname = info.nickname?.takeIf { it.isNotBlank() } ?: "회원"
                    email = info.email
                    profileImageUrl = info.profileImageUrl
                    // profileCompletedAt = null → needsProfileSetup = true (프로필 설정 유도)
                },
            )
            oauthRepository.save(
                UserOAuthAccount().apply {
                    userId = created.id
                    provider = OAuthProvider.KAKAO
                    providerUserId = info.kakaoUserId
                },
            )
            created
        }
        return issueTokens(user)
    }

    /** 리프레시 토큰 원자적 회전 + 재사용 탐지 + 계정상태 검사. */
    @Transactional
    fun refresh(rawRefreshToken: String): TokenResponse {
        val hash = RandomTokens.sha256(rawRefreshToken)
        val stored = refreshTokenRepository.findByTokenHash(hash)
            ?: throw UnauthorizedException("유효하지 않은 토큰입니다.")
        val now = Instant.now()

        // 이미 폐기된 토큰 재제시 = 재사용(탈취 정황) → 별도 tx로 전 세션 즉시 폐기(현 tx 롤백에도 커밋).
        if (stored.revokedAt != null) {
            sessionRevoker.revokeAllSessions(stored.userId, now)
            log.warn("Refresh token reuse detected for userId={} — revoked all sessions", stored.userId)
            throw UnauthorizedException("유효하지 않은 토큰입니다.")
        }
        if (stored.expiresAt.isBefore(now)) {
            throw UnauthorizedException("만료된 토큰입니다.")
        }
        // 원자적 회전: 활성일 때만 폐기 성공. 0이면 동시 요청이 이미 회전 → 재사용으로 간주.
        if (refreshTokenRepository.revokeIfActive(stored.id, now) == 0) {
            sessionRevoker.revokeAllSessions(stored.userId, now)
            log.warn("Concurrent/reused refresh for userId={} — revoked all sessions", stored.userId)
            throw UnauthorizedException("유효하지 않은 토큰입니다.")
        }

        val user = userRepository.findById(stored.userId).orElseThrow { UnauthorizedException() }
        if (user.status != UserStatus.ACTIVE) {
            sessionRevoker.revokeAllSessions(user.id, now)
            throw UnauthorizedException("이용할 수 없는 계정입니다.")
        }
        return issueTokens(user)
    }

    /** 제시된 리프레시 토큰 폐기(로그아웃). 존재하지 않아도 조용히 성공(정보 노출 방지). */
    @Transactional
    fun logout(rawRefreshToken: String) {
        val hash = RandomTokens.sha256(rawRefreshToken)
        refreshTokenRepository.findByTokenHash(hash)?.let {
            if (it.revokedAt == null) {
                it.revokedAt = Instant.now()
                refreshTokenRepository.save(it)
            }
        }
    }

    private fun requireActive(user: User) {
        when (user.status) {
            UserStatus.ACTIVE -> return
            UserStatus.SUSPENDED -> throw ForbiddenException("정지된 계정입니다.")
            UserStatus.WITHDRAWN -> throw ForbiddenException("탈퇴한 계정입니다.")
        }
    }

    private fun issueTokens(user: User): TokenResponse {
        val access = tokenProvider.issueAccessToken(user.id)
        val rawRefresh = RandomTokens.generate()
        refreshTokenRepository.save(
            RefreshToken().apply {
                userId = user.id
                tokenHash = RandomTokens.sha256(rawRefresh)
                expiresAt = Instant.now().plus(jwtProperties.refreshTokenTtl)
            },
        )
        return TokenResponse(
            accessToken = access,
            refreshToken = rawRefresh,
            expiresIn = tokenProvider.accessTokenTtlSeconds,
            user = UserResponse.from(user),
        )
    }
}
