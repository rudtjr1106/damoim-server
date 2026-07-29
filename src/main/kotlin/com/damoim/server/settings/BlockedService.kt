package com.damoim.server.settings

import com.damoim.server.club.MembershipService
import com.damoim.server.common.BadRequestException
import com.damoim.server.common.NotFoundException
import com.damoim.server.common.TimeLabels
import com.damoim.server.domain.entity.BlockedUser
import com.damoim.server.domain.entity.User
import com.damoim.server.domain.enums.UserStatus
import com.damoim.server.domain.repository.BlockedUserRepository
import com.damoim.server.domain.repository.UserRepository
import com.damoim.server.storage.StorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 차단 관리(83) — 동아리장 전용. 차단 해제는 하드 삭제.
 * 차단은 동아리 단위(blocked_users는 club_id + blocked_user_id)라 등록하면 그 회원의 글·댓글이
 * 동아리 전체에서 감춰진다(실제 필터는 BoardService).
 */
@Service
class BlockedService(
    private val membership: MembershipService,
    private val blockedUserRepository: BlockedUserRepository,
    private val userRepository: UserRepository,
    private val storageService: StorageService,
) {
    @Transactional(readOnly = true)
    fun list(userId: Long): List<BlockedUserResponse> {
        val clubId = membership.requireLeader(userId).clubId
        val blocked = blockedUserRepository.findByClubIdOrderByBlockedAtDesc(clubId)
        if (blocked.isEmpty()) return emptyList()
        val usersById = userRepository.findAllById(blocked.map { it.blockedUserId }).associateBy { it.id }
        return blocked.map { toResponse(it, usersById[it.blockedUserId]) }
    }

    /**
     * 83 차단 등록. 자기 자신은 차단 불가, 이미 차단된 회원은 기존 항목을 그대로 반환(멱등 —
     * 재시도·중복 탭이 유니크 인덱스 위반으로 500이 되지 않게).
     */
    @Transactional
    fun block(userId: Long, req: BlockUserRequest): BlockedUserResponse {
        val clubId = membership.requireLeader(userId).clubId
        val targetId = req.userId ?: throw BadRequestException("차단할 회원이 필요합니다.")
        if (targetId == userId) throw BadRequestException("자기 자신은 차단할 수 없습니다.", "CANNOT_BLOCK_SELF")
        val target = userRepository.findById(targetId)
            .orElseThrow { NotFoundException("회원을 찾을 수 없습니다.") }
        val existing = blockedUserRepository.findByClubIdAndBlockedUserId(clubId, targetId)
        if (existing != null) return toResponse(existing, target)
        val saved = blockedUserRepository.save(
            BlockedUser().apply {
                this.clubId = clubId
                blockedUserId = targetId
                // 83 배지 — 탈퇴 회원은 이름·사진을 숨긴다(등록 시점 상태를 박제).
                isWithdrawn = target.status == UserStatus.WITHDRAWN
                blockedAt = Instant.now()
            },
        )
        return toResponse(saved, target)
    }

    /** 차단 해제 — 대상이 이 동아리 차단 항목인지 검증(IDOR) 후 하드 삭제. */
    @Transactional
    fun unblock(userId: Long, blockedId: Long) {
        val clubId = membership.requireLeader(userId).clubId
        val blocked = blockedUserRepository.findById(blockedId)
            .orElseThrow { NotFoundException("차단 항목을 찾을 수 없습니다.") }
        if (blocked.clubId != clubId) throw NotFoundException("차단 항목을 찾을 수 없습니다.")
        blockedUserRepository.delete(blocked)
    }

    private fun toResponse(blocked: BlockedUser, user: User?): BlockedUserResponse {
        val name = if (blocked.isWithdrawn) "탈퇴한 사용자" else (user?.nickname ?: "탈퇴한 사용자")
        return BlockedUserResponse(
            id = blocked.id,
            name = name,
            initials = if (blocked.isWithdrawn) "익명" else initialsOf(name),
            blockedLabel = "${TimeLabels.date(blocked.blockedAt)} 차단",
            isWithdrawn = blocked.isWithdrawn,
            imageUrl = if (blocked.isWithdrawn) null else blockedImageUrl(user),
        )
    }

    private fun initialsOf(name: String) = if (name.length >= 3) name.takeLast(2) else name

    /** 차단 대상 프로필 사진 URL — 내부 업로드 키가 있으면 presigned view, 없으면 외부(카카오) URL. */
    private fun blockedImageUrl(u: User?): String? =
        u?.profileImageKey?.let { storageService.presignView(it) } ?: u?.profileImageUrl
}
