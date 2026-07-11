package com.damoim.server.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

/**
 * 자체 액세스 JWT 발급/검증. HS256, subject = userId.
 *
 * 키 결정(보안):
 *  - app.jwt.secret 제공 + 32바이트 이상 → 그 키 사용.
 *  - 미제공(blank) & prod 프로파일 → 부팅 실패(fail-fast). 운영은 반드시 JWT_SECRET 주입.
 *  - 미제공 & 비-prod → 임시 랜덤 키 생성(재시작마다 무효화). 커밋/패키징된 알려진 키가 없어 위조 불가.
 */
@Component
class JwtTokenProvider(props: JwtProperties, environment: Environment) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val issuer = props.issuer
    private val accessTtl = props.accessTokenTtl
    private val key: SecretKey = resolveKey(props.secret, environment)

    private fun resolveKey(secret: String, env: Environment): SecretKey {
        if (secret.isBlank()) {
            val isProd = env.activeProfiles.contains("prod")
            check(!isProd) {
                "app.jwt.secret(JWT_SECRET)은 운영에서 필수입니다. 32바이트 이상의 강한 키를 주입하세요."
            }
            log.warn("app.jwt.secret 미설정 — 개발용 임시 랜덤 키를 생성합니다(재시작 시 기존 토큰 무효화). 운영 금지.")
            return Jwts.SIG.HS256.key().build()
        }
        val bytes = secret.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size >= 32) {
            "app.jwt.secret must be at least 32 bytes for HS256."
        }
        return Keys.hmacShaKeyFor(bytes)
    }

    fun issueAccessToken(userId: Long, now: Instant = Instant.now()): String =
        Jwts.builder()
            .issuer(issuer)
            .subject(userId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTtl)))
            .signWith(key, Jwts.SIG.HS256)
            .compact()

    /** 유효하면 userId, 아니면 null(만료·서명불일치·형식오류·발급자불일치 전부 흡수). */
    fun parseUserId(token: String): Long? =
        try {
            Jwts.parser()
                .requireIssuer(issuer)
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
                .subject
                .toLong()
        } catch (e: Exception) {
            null
        }

    val accessTokenTtlSeconds: Long get() = accessTtl.seconds
}
