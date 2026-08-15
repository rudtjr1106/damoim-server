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
 *
 * @param trustedProxyHops 앱 앞단에 있는 **신뢰 프록시 홉 수**. X-Forwarded-For를 어디까지 믿을지 결정한다
 *   ([clientIp] 주석 참고). 0이면 XFF를 아예 무시하고 remoteAddr만 쓴다.
 */
class RateLimitFilter(
    private val trustedProxyHops: Int = DEFAULT_TRUSTED_PROXY_HOPS,
) : OncePerRequestFilter() {

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

    /**
     * 버킷 키로 쓸 클라이언트 IP.
     *
     * X-Forwarded-For는 클라이언트가 마음대로 넣어 보낼 수 있어, 값을 그대로 키로 쓰면 매 요청 다른 IP를
     * 위조해 로그인 브루트포스 억제(auth 규칙)를 통째로 무력화할 수 있다.
     *
     * 그래서 **오른쪽에서부터** 센다: 프록시는 자기가 실제로 본 접속 주소를 헤더 오른쪽 끝에 덧붙이므로
     * 오른쪽 토큰일수록 우리 인프라가 관측한 값이고, 왼쪽 토큰은 클라이언트가 심어 보낸 값일 수 있다.
     * 신뢰 프록시 홉이 N이면 오른쪽에서 N번째 토큰이 "가장 바깥 신뢰 프록시가 본 접속자"다.
     *   예) N=1, 위조 헤더로 "1.1.1.1"을 보내면 Funnel이 실제 주소를 덧붙여 "1.1.1.1, 9.9.9.9" →
     *       오른쪽 1번째인 9.9.9.9(진짜 접속자)를 채택하고 위조분은 버린다.
     *
     * 토큰 수가 홉 수보다 적거나(헤더 없음·프록시 미경유) IP 형식이 아니면 remoteAddr로 폴백한다.
     */
    private fun clientIp(request: HttpServletRequest): String {
        if (trustedProxyHops <= 0) return request.remoteAddr   // 프록시 없는 배치 = XFF 전면 무시
        val tokens = request.getHeader("X-Forwarded-For")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val candidate = tokens.getOrNull(tokens.size - trustedProxyHops) ?: return request.remoteAddr
        return normalizeIp(candidate) ?: request.remoteAddr
    }

    /** "1.2.3.4:5678", "[::1]:443" 같은 포트 표기를 벗기고 IP 형식이면 반환, 아니면 null(→ 폴백). */
    private fun normalizeIp(raw: String): String? {
        val value = when {
            raw.startsWith("[") -> raw.substringAfter('[').substringBefore(']')  // [IPv6] / [IPv6]:port
            raw.count { it == ':' } == 1 -> raw.substringBefore(':')             // IPv4:port
            else -> raw
        }
        return value.takeIf { IPV4.matches(it) || IPV6_LOOSE.matches(it) }
    }

    companion object {
        /**
         * 기본 신뢰 홉 수. 현재 배치는 [클라이언트] → Tailscale Funnel(호스트) → 앱 이라 신뢰 프록시가 1홉이다.
         * 앞단이 늘거나(예: CDN+리버스프록시=2) 없어지면 app.security.trusted-proxy-hops로 조정한다.
         */
        const val DEFAULT_TRUSTED_PROXY_HOPS = 1

        private val IPV4 = Regex("""^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$""")

        // IPv6 축약·IPv4매핑까지 엄밀히 검증할 필요는 없다(키로만 쓰인다). 문자 집합·길이만 제한해
        // 헤더에 임의 문자열을 넣어 키 공간을 늘리는 것만 막는다.
        private val IPV6_LOOSE = Regex("""^(?=.*:)[0-9A-Fa-f:.]{2,45}$""")
    }
}
