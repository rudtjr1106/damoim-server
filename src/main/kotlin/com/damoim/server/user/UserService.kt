package com.damoim.server.user

import com.damoim.server.common.BadRequestException
import com.damoim.server.common.ForbiddenException
import com.damoim.server.common.NotFoundException
import com.damoim.server.domain.repository.UserRepository
import com.damoim.server.storage.StorageKeys
import com.damoim.server.storage.StorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class UserService(
    private val userRepository: UserRepository,
    private val storageService: StorageService,
    private val responseMapper: UserResponseMapper,
) {

    @Transactional(readOnly = true)
    fun getMe(userId: Long): UserResponse =
        responseMapper.toResponse(userRepository.findById(userId).orElseThrow { NotFoundException("사용자를 찾을 수 없습니다.") })

    /** 프로필 사진 업로드 URL 발급(1단계). 클라가 이 URL로 S3에 직접 PUT 후, key를 프로필 저장에 전달. */
    @Transactional(readOnly = true)
    fun createProfileImageUploadUrl(userId: Long, req: ProfileImageUploadRequest): ProfileImageUploadResponse {
        if (req.sizeBytes > PROFILE_MAX_BYTES) throw BadRequestException("이미지가 너무 큽니다.", "FILE_TOO_LARGE")
        val up = storageService.presignUpload(
            StorageKeys.forProfile(userId, req.fileName?.takeIf { it.isNotBlank() } ?: "avatar"),
            req.contentType,
        )
        return ProfileImageUploadResponse(up.url, up.key, up.expiresInSeconds)
    }

    /** 프로필 설정(31)·수정(45). 최초 완료 시 profileCompletedAt 세팅 → needsProfileSetup=false. */
    @Transactional
    fun updateProfile(userId: Long, req: UpdateProfileRequest): UserResponse {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("사용자를 찾을 수 없습니다.") }
        user.nickname = req.nickname.trim()
        user.contact = req.contact?.trim()?.takeIf { it.isNotEmpty() }
        // 앱 업로드 사진(내부 키): 소유권·실오브젝트 검증 후 저장(외부 URL보다 우선).
        req.profileImageKey?.takeIf { it.isNotBlank() }?.let { key ->
            if (!key.startsWith("profiles/$userId/")) {
                throw ForbiddenException("잘못된 업로드 키입니다.", "INVALID_STORAGE_KEY")
            }
            if (storageService.verifiesSize && storageService.objectSizeOrNull(key) == null) {
                throw BadRequestException("업로드가 완료되지 않았습니다.", "UPLOAD_INCOMPLETE")
            }
            user.profileImageKey = key
        }
        // 외부 http(s) URL(카카오 등)만 별도 저장(내부 키가 없을 때 사용).
        req.profileImageUrl?.takeIf { it.isNotBlank() }?.let { user.profileImageUrl = it }
        if (user.profileCompletedAt == null) {
            user.profileCompletedAt = Instant.now()
        }
        return responseMapper.toResponse(userRepository.save(user))
    }

    private companion object {
        const val PROFILE_MAX_BYTES = 5L * 1024 * 1024  // 프로필 사진 상한 5MB
    }
}
