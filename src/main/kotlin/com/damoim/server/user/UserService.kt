package com.damoim.server.user

import com.damoim.server.auth.SessionRevoker
import com.damoim.server.club.ClubService
import com.damoim.server.common.BadRequestException
import com.damoim.server.common.ConflictException
import com.damoim.server.common.ForbiddenException
import com.damoim.server.common.NotFoundException
import com.damoim.server.domain.enums.MemberRole
import com.damoim.server.domain.enums.MemberStatus
import com.damoim.server.domain.enums.UserStatus
import com.damoim.server.domain.repository.ClubMemberRepository
import com.damoim.server.domain.repository.UserOAuthAccountRepository
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
    private val clubMemberRepository: ClubMemberRepository,
    private val clubService: ClubService,
    private val userOAuthAccountRepository: UserOAuthAccountRepository,
    private val sessionRevoker: SessionRevoker,
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

    /**
     * 51 회원 탈퇴 — 소프트 탈퇴(status=WITHDRAWN). 로그아웃/동아리 탈퇴와 구분된다.
     * 단독 리더인 동아리는 통째 삭제(좀비 방지), 다른 회원이 있는 동아리의 리더면 위임을 요구해 전체 롤백.
     * PII를 지우고 소셜 링크를 삭제(재가입 허용)하며 전 세션을 즉시 폐기한다.
     * 작성 콘텐츠(author_id/uploader_id)는 소프트 탈퇴라 보존되고 '탈퇴한 사용자'로 표시된다.
     */
    @Transactional
    fun withdraw(userId: Long) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("사용자를 찾을 수 없습니다.") }
        if (user.status == UserStatus.WITHDRAWN) return   // 멱등

        val memberships = clubMemberRepository.findByUserIdAndStatus(userId, MemberStatus.ACTIVE)
        // 1) 먼저 전부 검증 — 다른 회원이 있는 동아리의 리더면 위임 요구(부분 삭제 방지 위해 삭제 전 검사).
        memberships.forEach { m ->
            if (m.memberRole == MemberRole.LEADER &&
                clubMemberRepository.countByClubIdAndStatus(m.clubId, MemberStatus.ACTIVE) > 1
            ) {
                throw ConflictException(
                    "동아리장인 동아리가 있어요. 먼저 다른 회원에게 위임한 뒤 탈퇴해주세요.",
                    "LEADER_MUST_DELEGATE",
                )
            }
        }
        // 2) 실제 정리 — 단독 리더 동아리는 통째 삭제, 그 외 멤버십은 행 삭제.
        memberships.forEach { m ->
            val sole = clubMemberRepository.countByClubIdAndStatus(m.clubId, MemberStatus.ACTIVE) == 1L
            if (m.memberRole == MemberRole.LEADER && sole) {
                clubService.deleteClubCascade(m.clubId)   // 멤버십도 캐스케이드로 함께 삭제됨
            } else {
                clubMemberRepository.delete(m)
            }
        }
        // 3) 계정 소프트 탈퇴 + PII 스크럽.
        user.status = UserStatus.WITHDRAWN
        user.activeClubId = null
        user.email = null
        user.contact = null
        user.profileImageUrl = null
        user.profileImageKey = null
        userRepository.save(user)
        // 4) 소셜 링크 삭제(같은 카카오로 새 계정 재가입 가능 — WITHDRAWN 부활 방지) + 전 세션 폐기.
        userOAuthAccountRepository.deleteByUserId(userId)
        sessionRevoker.revokeAllSessions(userId, Instant.now())
    }

    private companion object {
        const val PROFILE_MAX_BYTES = 5L * 1024 * 1024  // 프로필 사진 상한 5MB
    }
}
