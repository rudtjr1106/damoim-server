package com.damoim.server.config

import com.damoim.server.security.JwtAuthenticationFilter
import com.damoim.server.security.JwtTokenProvider
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

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

        http {
            csrf { disable() }
            httpBasic { disable() }
            formLogin { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }

            authorizeHttpRequests {
                authorize("/api/auth/**", permitAll)
                authorize("/api/ping", permitAll)
                authorize("/actuator/health", permitAll)
                authorize("/actuator/health/**", permitAll)
                authorize("/error", permitAll)
                authorize(anyRequest, authenticated)
            }

            exceptionHandling {
                authenticationEntryPoint = org.springframework.security.web.AuthenticationEntryPoint { _, res, _ ->
                    writeJson(res, HttpStatus.UNAUTHORIZED, "인증이 필요합니다.")
                }
                accessDeniedHandler = org.springframework.security.web.access.AccessDeniedHandler { _, res, _ ->
                    writeJson(res, HttpStatus.FORBIDDEN, "권한이 없습니다.")
                }
            }
        }
        return http.build()
    }

    private fun writeJson(res: HttpServletResponse, status: HttpStatus, message: String) {
        res.status = status.value()
        res.contentType = "application/json;charset=UTF-8"
        res.writer.write("""{"status":${status.value()},"message":"$message"}""")
    }
}
