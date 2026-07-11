package com.damoim.server.user

import com.damoim.server.common.NotFoundException
import com.damoim.server.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class UserService(private val userRepository: UserRepository) {

    @Transactional(readOnly = true)
    fun getMe(userId: Long): UserResponse =
        UserResponse.from(userRepository.findById(userId).orElseThrow { NotFoundException("사용자를 찾을 수 없습니다.") })

    /** 프로필 설정(31). 최초 완료 시 profileCompletedAt 세팅 → needsProfileSetup=false. */
    @Transactional
    fun updateProfile(userId: Long, req: UpdateProfileRequest): UserResponse {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("사용자를 찾을 수 없습니다.") }
        user.nickname = req.nickname.trim()
        user.contact = req.contact?.trim()?.takeIf { it.isNotEmpty() }
        req.profileImageUrl?.let { user.profileImageUrl = it }
        if (user.profileCompletedAt == null) {
            user.profileCompletedAt = Instant.now()
        }
        return UserResponse.from(userRepository.save(user))
    }
}
