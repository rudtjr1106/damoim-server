package com.damoim.server.settings

import com.damoim.server.club.MembershipService
import com.damoim.server.common.NotFoundException
import com.damoim.server.common.TimeLabels
import com.damoim.server.domain.repository.BlockedUserRepository
import com.damoim.server.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 차단 관리(83) — 동아리장 전용. 차단 해제는 하드 삭제. */
@Service
class BlockedService(
    private val membership: MembershipService,
    private val blockedUserRepository: BlockedUserRepository,
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun list(userId: Long): List<BlockedUserResponse> {
        val clubId = membership.requireLeader(userId).clubId
        val blocked = blockedUserRepository.findByClubIdOrderByBlockedAtDesc(clubId)
        if (blocked.isEmpty()) return emptyList()
        val names = userRepository.findAllById(blocked.map { it.blockedUserId }).associate { it.id to it.nickname }
        return blocked.map {
            val name = if (it.isWithdrawn) "탈퇴한 사용자" else (names[it.blockedUserId] ?: "탈퇴한 사용자")
            BlockedUserResponse(
                id = it.id,
                name = name,
                initials = if (it.isWithdrawn) "익명" else initialsOf(name),
                blockedLabel = "${TimeLabels.date(it.blockedAt)} 차단",
                isWithdrawn = it.isWithdrawn,
            )
        }
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

    private fun initialsOf(name: String) = if (name.length >= 3) name.takeLast(2) else name
}
