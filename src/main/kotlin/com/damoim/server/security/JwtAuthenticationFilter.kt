package com.damoim.server.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authorization: Bearer <access-token>을 검증해 SecurityContext에 인증을 세팅한다.
 * 토큰이 없거나 유효하지 않으면 그냥 통과시키고(SecurityContext 비움) 인가 단계에서 401 처리한다.
 * — 필터에서 직접 예외를 던지지 않아 정보 노출을 줄인다.
 */
class JwtAuthenticationFilter(
    private val tokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = resolveBearer(request)
        if (token != null) {
            val userId = tokenProvider.parseUserId(token)
            if (userId != null && SecurityContextHolder.getContext().authentication == null) {
                val principal = UserPrincipal(userId)
                val auth = UsernamePasswordAuthenticationToken(principal, null, emptyList())
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun resolveBearer(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        return if (header.startsWith("Bearer ", ignoreCase = true)) header.substring(7).trim() else null
    }
}
