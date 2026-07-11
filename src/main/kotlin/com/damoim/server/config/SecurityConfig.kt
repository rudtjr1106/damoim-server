package com.damoim.server.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain

/**
 * 개발용 임시 보안 설정 — 스캐폴딩 단계라 **모든 요청을 허용**한다.
 *
 * spring-boot-starter-security를 넣으면 기본값이 "모든 엔드포인트를 임의 비밀번호로 잠금"이라
 * /api/ping·/actuator/health까지 막힌다. 이를 풀어 개발을 진행할 수 있게 permitAll로 연다.
 *
 * TODO(인증 도입): 카카오 OAuth 서버측 검증 + JWT 필터를 붙일 때 이 설정을 교체한다.
 *   - 공개 엔드포인트(로그인, health)만 permitAll
 *   - 나머지는 authenticated + JwtAuthenticationFilter
 */
@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }                 // 무상태 REST API — CSRF 토큰 불필요
            httpBasic { disable() }
            formLogin { disable() }
            authorizeHttpRequests {
                authorize(anyRequest, permitAll)
            }
        }
        return http.build()
    }
}
