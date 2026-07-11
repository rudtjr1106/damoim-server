package com.damoim.server.auth

import com.damoim.server.user.UserResponse
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 카카오 로그인 요청 — 클라가 카카오 SDK로 받은 access token. */
data class KakaoLoginRequest(
    @field:NotBlank(message = "accessToken은 필수입니다.")
    @field:Size(max = 4096, message = "accessToken이 너무 깁니다.")
    val accessToken: String,
)

data class RefreshRequest(
    @field:NotBlank(message = "refreshToken은 필수입니다.")
    @field:Size(max = 512, message = "refreshToken이 너무 깁니다.")
    val refreshToken: String,
)

data class LogoutRequest(
    @field:NotBlank(message = "refreshToken은 필수입니다.")
    @field:Size(max = 512, message = "refreshToken이 너무 깁니다.")
    val refreshToken: String,
)

/** 로그인/재발급 응답. accessToken은 Bearer로, refreshToken은 안전 저장(클라). */
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,        // accessToken 만료까지 초
    val user: UserResponse,
)
