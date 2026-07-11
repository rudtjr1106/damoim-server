package com.damoim.server.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * app.jwt.* 설정. secret은 운영에서 반드시 환경변수(JWT_SECRET)로 주입한다.
 * 최소 길이 검증은 JwtTokenProvider 초기화 시 수행(fail-fast).
 */
@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
    val issuer: String,
)
