package com.damoim.server.security

import org.springframework.http.HttpMethod

/** 레이트리밋 키 기준 — 미인증 엔드포인트는 IP, 인증 엔드포인트는 userId. */
enum class RateKey { IP, USER }

/** 엔드포인트별 정책. capacity = 분당 허용 요청 수(greedy refill). */
data class RateLimitRule(
    val name: String,
    val method: HttpMethod,
    val pathPattern: String,   // AntPathMatcher 패턴
    val key: RateKey,
    val perMinute: Long,
)

/**
 * 민감/고비용/남용 가능 엔드포인트에만 적용(allow-by-default, 목록에 없으면 무제한 통과).
 * 인프라(ALB/WAF/게이트웨이) 레이트리밋과 병행하는 애플리케이션 방어선.
 */
object RateLimitPolicy {
    val RULES = listOf(
        // 미인증 공개 — 로그인/토큰 브루트포스·스터핑 억제(IP 기준)
        RateLimitRule("auth", HttpMethod.POST, "/api/auth/**", RateKey.IP, 10),
        // 가입 코드 브루트포스 억제
        RateLimitRule("join", HttpMethod.POST, "/api/clubs/join", RateKey.USER, 5),
        // 게시글 스팸 도배 방지
        RateLimitRule("post-create", HttpMethod.POST, "/api/board/posts", RateKey.USER, 10),
        // 고비용 검색 폭주 방지
        RateLimitRule("search", HttpMethod.GET, "/api/board/search", RateKey.USER, 30),
        // 결제 활성화 중복/폭주 방지
        RateLimitRule("subscribe", HttpMethod.POST, "/api/subscription/subscribe", RateKey.USER, 5),
    )
}
