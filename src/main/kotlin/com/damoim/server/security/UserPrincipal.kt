package com.damoim.server.security

/**
 * 인증된 사용자 식별자. JWT 필터가 SecurityContext의 principal로 넣고,
 * 컨트롤러는 @AuthenticationPrincipal UserPrincipal로 받는다.
 */
data class UserPrincipal(val userId: Long)
