package com.damoim.server.user

import com.damoim.server.domain.entity.User
import com.damoim.server.storage.StorageService
import org.springframework.stereotype.Component

/**
 * [UserResponse] 생성 공용 매퍼. 내부 업로드 프로필 사진([User.profileImageKey])이 있으면
 * presigned view URL로 파생하고, 없으면 외부 URL([User.profileImageUrl], 예: 카카오)을 그대로 쓴다.
 * 로그인([com.damoim.server.auth.AuthService])·프로필([UserService]) 응답이 공용.
 */
@Component
class UserResponseMapper(private val storageService: StorageService) {

    fun toResponse(u: User) = UserResponse(
        id = u.id,
        nickname = u.nickname,
        email = u.email,
        profileImageUrl = u.profileImageKey?.let { storageService.presignView(it) } ?: u.profileImageUrl,
        contact = u.contact,
        needsProfileSetup = u.profileCompletedAt == null,
    )
}
