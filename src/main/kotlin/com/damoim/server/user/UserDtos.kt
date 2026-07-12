package com.damoim.server.user

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** 프로필 설정/수정 요청(31/45). */
data class UpdateProfileRequest(
    @field:NotBlank(message = "이름은 필수입니다.")
    @field:Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    val nickname: String,

    @field:Pattern(regexp = "^$|^\\d{10,11}$", message = "연락처는 숫자 10~11자리여야 합니다.")
    val contact: String? = null,

    /** 외부 http(s) 이미지 URL(예: 카카오 프로필). 앱에서 올린 사진은 [profileImageKey]를 쓴다. */
    @field:Size(max = 1000, message = "프로필 이미지 URL이 너무 깁니다.")
    @field:Pattern(regexp = "^$|^https?://.+", message = "이미지 URL은 http(s):// 형식이어야 합니다.")
    val profileImageUrl: String? = null,

    /** 앱에서 presigned PUT으로 올린 프로필 사진의 S3 키. 서버가 소유권 검증 후 저장. */
    @field:Size(max = 1024)
    val profileImageKey: String? = null,
)

/** 프로필 사진 업로드 URL 요청(1단계). */
data class ProfileImageUploadRequest(
    @field:Size(max = 255) val fileName: String? = null,
    @field:Size(max = 255) val contentType: String? = null,
    @field:Min(value = 1, message = "파일 크기가 올바르지 않습니다.")
    val sizeBytes: Long,
)

/** 프로필 사진 업로드 URL 응답 — 클라가 이 URL로 S3에 직접 PUT 후 key를 프로필 저장에 전달. */
data class ProfileImageUploadResponse(
    val uploadUrl: String,
    val storageKey: String,
    val expiresInSeconds: Long,
)

/**
 * 사용자 응답(로그인·/me 공용). 민감 필드(oauth id 등) 미포함.
 * profileImageUrl은 내부 업로드 키가 있으면 presigned view URL로 파생 → [UserResponseMapper]가 생성.
 */
data class UserResponse(
    val id: Long,
    val nickname: String,
    val email: String?,
    val profileImageUrl: String?,
    val contact: String?,
    val needsProfileSetup: Boolean,
)
