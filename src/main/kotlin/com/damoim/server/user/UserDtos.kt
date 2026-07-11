package com.damoim.server.user

import com.damoim.server.domain.entity.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** 프로필 설정/수정 요청(31). */
data class UpdateProfileRequest(
    @field:NotBlank(message = "이름은 필수입니다.")
    @field:Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    val nickname: String,

    @field:Pattern(regexp = "^$|^\\d{10,11}$", message = "연락처는 숫자 10~11자리여야 합니다.")
    val contact: String? = null,

    @field:Size(max = 1000, message = "프로필 이미지 URL이 너무 깁니다.")
    @field:Pattern(regexp = "^$|^https?://.+", message = "이미지 URL은 http(s):// 형식이어야 합니다.")
    val profileImageUrl: String? = null,
)

/** 사용자 응답(로그인·/me 공용). 민감 필드(oauth id 등) 미포함. */
data class UserResponse(
    val id: Long,
    val nickname: String,
    val email: String?,
    val profileImageUrl: String?,
    val contact: String?,
    val needsProfileSetup: Boolean,
) {
    companion object {
        fun from(u: User) = UserResponse(
            id = u.id,
            nickname = u.nickname,
            email = u.email,
            profileImageUrl = u.profileImageUrl,
            contact = u.contact,
            needsProfileSetup = u.profileCompletedAt == null,
        )
    }
}
