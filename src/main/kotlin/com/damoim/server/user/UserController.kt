package com.damoim.server.user

import com.damoim.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/me")
class UserController(private val userService: UserService) {

    /** 현재 사용자. principal.userId는 서버가 검증한 JWT에서만 나온다(클라 입력 아님). */
    @GetMapping
    fun me(@AuthenticationPrincipal principal: UserPrincipal): UserResponse =
        userService.getMe(principal.userId)

    /** 프로필 설정/수정. 대상은 항상 인증 주체 본인 — path에 userId를 받지 않아 IDOR 원천 차단. */
    @PatchMapping("/profile")
    fun updateProfile(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody req: UpdateProfileRequest,
    ): UserResponse =
        userService.updateProfile(principal.userId, req)

    /** 프로필 사진 업로드 URL 발급(1단계) — presigned PUT. 올린 뒤 key를 PATCH /profile로 저장. */
    @PostMapping("/profile-image")
    fun profileImageUploadUrl(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody req: ProfileImageUploadRequest,
    ): ProfileImageUploadResponse =
        userService.createProfileImageUploadUrl(principal.userId, req)
}
