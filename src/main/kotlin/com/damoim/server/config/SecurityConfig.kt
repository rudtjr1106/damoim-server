package com.damoim.server.config

import com.damoim.server.security.JwtAuthenticationFilter
import com.damoim.server.security.JwtTokenProvider
import com.damoim.server.security.RateLimitFilter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter

/**
 * 무상태(JWT) 보안 설정.
 * - 세션 미사용, CSRF/폼로그인/기본인증 비활성
 * - 공개: 인증 엔드포인트·헬스체크만. 그 외 전부 인증 필요(deny-by-default)
 * - JWT 필터가 Bearer 토큰을 검증해 SecurityContext 세팅
 * - 미인증/권한없음은 JSON 401/403으로 응답(HTML 리다이렉트 없음)
 */
@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity, tokenProvider: JwtTokenProvider): SecurityFilterChain {
        val jwtFilter = JwtAuthenticationFilter(tokenProvider)

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
        // 레이트리밋은 JWT 필터 뒤(USER 키가 principal 사용). 미인증 경로는 IP로 폴백.
        http.addFilterAfter(RateLimitFilter(), JwtAuthenticationFilter::class.java)

        http {
            csrf { disable() }
            httpBasic { disable() }
            formLogin { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }

            // 보안 응답 헤더(하드닝). HSTS는 HTTPS(prod, forward-headers-strategy) 요청에서만 방출된다.
            // X-Frame-Options: DENY 와 X-Content-Type-Options: nosniff 는 Spring Security 기본값.
            headers {
                referrerPolicy { policy = ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN }
                httpStrictTransportSecurity {
                    includeSubDomains = true
                    maxAgeInSeconds = 31_536_000                          // 1년(HTTPS 요청에서만 방출)
                }
            }

            authorizeHttpRequests {
                authorize("/api/auth/**", permitAll)
                // 로컬 개발 스토리지(provider=local) — 클라가 presigned URL로 인증 없이 직접 PUT/GET.
                // 운영(S3)에선 해당 컨트롤러가 없어 404이므로 무해.
                authorize("/_localstorage/**", permitAll)
                authorize("/api/ping", permitAll)
                authorize("/actuator/health", permitAll)
                authorize("/actuator/health/**", permitAll)
                authorize("/error", permitAll)
                // API 문서(OpenAPI/Swagger UI) — 공개
                authorize("/swagger-ui.html", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize("/v3/api-docs", permitAll)
                authorize("/v3/api-docs/**", permitAll)
                authorize(anyRequest, authenticated)
            }

            exceptionHandling {
                authenticationEntryPoint = org.springframework.security.web.AuthenticationEntryPoint { _, res, _ ->
                    writeEnvelope(res, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다.")
                }
                accessDeniedHandler = org.springframework.security.web.access.AccessDeniedHandler { _, res, _ ->
                    writeEnvelope(res, HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다.")
                }
            }
        }
        return http.build()
    }

    /** 필터 단계 응답은 메시지 컨버터를 안 타므로 공통 봉투 JSON을 직접 쓴다. */
    private fun writeEnvelope(res: HttpServletResponse, status: HttpStatus, code: String, message: String) {
        res.status = status.value()
        res.contentType = "application/json;charset=UTF-8"
        res.writer.write("""{"success":false,"data":null,"error":{"code":"$code","message":"$message"}}""")
    }
}
