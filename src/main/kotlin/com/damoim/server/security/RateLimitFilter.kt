package com.damoim.server.security

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

/**
 * 인메모리 레이트리밋 필터([RateLimitPolicy] 정책 적용). JWT 필터 뒤에 등록되어 USER 키에 principal을 쓴다.
 * 버킷 레지스트리는 Caffeine으로 상한/만료를 둬 IP 로테이션에 의한 무한 증식을 방지한다.
 * 초과 시 공통 봉투 {success,data,error, code:RATE_LIMITED}로 429 반환.
 */
class RateLimitFilter : OncePerRequestFilter() {

    private val matcher = AntPathMatcher()
    private val buckets = Caffeine.newBuilder()
        .maximumSize(100_000)
        .expireAfterAccess(Duration.ofMinutes(10))
        .build<String, Bucket>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val rule = RateLimitPolicy.RULES.firstOrNull {
            it.method.name() == request.method && matcher.match(it.pathPattern, request.requestURI)
        }
        if (rule == null) {
            filterChain.doFilter(request, response)
            return
        }
        val subject = when (rule.key) {
            RateKey.USER -> currentUserId()?.toString() ?: clientIp(request)
            RateKey.IP -> clientIp(request)
        }
        val bucket = buckets.get("${rule.name}:$subject") { newBucket(rule.perMinute) }
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response)
        } else {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write(
                """{"success":false,"data":null,"error":{"code":"RATE_LIMITED","message":"요청이 너무 잦습니다. 잠시 후 다시 시도해주세요."}}""",
            )
        }
    }

    private fun newBucket(perMinute: Long): Bucket =
        Bucket.builder()
            .addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build())
            .build()

    private fun currentUserId(): Long? =
        (SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal)?.userId

    /** 프록시/ALB 뒤: X-Forwarded-For 첫 토큰 우선, 없으면 remoteAddr. */
    private fun clientIp(request: HttpServletRequest): String {
        val xff = request.getHeader("X-Forwarded-For")
        return if (!xff.isNullOrBlank()) xff.substringBefore(",").trim() else request.remoteAddr
    }
}
