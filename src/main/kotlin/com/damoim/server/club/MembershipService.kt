package com.damoim.server.club

import com.damoim.server.common.ForbiddenException
import com.damoim.server.common.NotFoundException
import com.damoim.server.common.UnauthorizedException
import com.damoim.server.domain.entity.ClubMember
import com.damoim.server.domain.enums.MemberRole
import com.damoim.server.domain.enums.MemberStatus
import com.damoim.server.domain.repository.ClubMemberRepository
import com.damoim.server.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 인가(권한) 해석의 단일 지점. **활성 동아리는 클라 입력이 아니라 인증 주체의 active_club_id에서만 해석**한다
 * → 임의 clubId로 남의 동아리를 조작하는 IDOR을 원천 차단. 운영 액션은 requireLeader로 게이팅.
 */
@Service
class MembershipService(
    private val userRepository: UserRepository,
    private val clubMemberRepository: ClubMemberRepository,
) {
    @Transactional(readOnly = true)
    fun currentClubId(userId: Long): Long {
        val user = userRepository.findById(userId).orElseThrow { UnauthorizedException() }
        return user.activeClubId ?: throw NotFoundException("소속된 동아리가 없습니다.", "NO_ACTIVE_CLUB")
    }

    @Transactional(readOnly = true)
    fun currentMembership(userId: Long): ClubMember {
        val clubId = currentClubId(userId)
        val membership = clubMemberRepository.findByClubIdAndUserId(clubId, userId)
            ?: throw ForbiddenException("동아리 회원이 아닙니다.", "NOT_A_MEMBER")
        // 활동 회원만 인가 통과 — 휴면/제명 상태는 운영·조회 액션 차단(권한 회수 실효성)
        if (membership.status != MemberStatus.ACTIVE) {
            throw ForbiddenException("활동 중인 회원이 아닙니다.", "NOT_A_MEMBER")
        }
        return membership
    }

    /** 운영진 액션 게이트 — 동아리장만 통과. */
    @Transactional(readOnly = true)
    fun requireLeader(userId: Long): ClubMember {
        val membership = currentMembership(userId)
        if (membership.memberRole != MemberRole.LEADER) {
            throw ForbiddenException("동아리장만 할 수 있습니다.", "NOT_LEADER")
        }
        return membership
    }
}
