package com.damoim.server.club

import com.damoim.server.common.ForbiddenException
import com.damoim.server.common.NotFoundException
import com.damoim.server.common.UnauthorizedException
import com.damoim.server.domain.entity.ClubMember
import com.damoim.server.domain.enums.MemberRole
import com.damoim.server.domain.enums.MemberStatus
import com.damoim.server.domain.enums.PermissionType
import com.damoim.server.domain.repository.AdminPermissionRepository
import com.damoim.server.domain.repository.AdminProfileRepository
import com.damoim.server.domain.repository.ClubMemberRepository
import com.damoim.server.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 인가(권한) 해석의 단일 지점. **활성 동아리는 클라 입력이 아니라 인증 주체의 active_club_id에서만 해석**한다
 * → 임의 clubId로 남의 동아리를 조작하는 IDOR을 원천 차단. 운영 액션은 requireLeader/requirePermission으로 게이팅.
 */
@Service
class MembershipService(
    private val userRepository: UserRepository,
    private val clubMemberRepository: ClubMemberRepository,
    private val adminProfileRepository: AdminProfileRepository,
    private val adminPermissionRepository: AdminPermissionRepository,
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

    /** 운영진 액션 게이트 — 동아리장만 통과. (역할 변경·위임·권한 부여 등 리더 전용 액션) */
    @Transactional(readOnly = true)
    fun requireLeader(userId: Long): ClubMember {
        val membership = currentMembership(userId)
        if (membership.memberRole != MemberRole.LEADER) {
            throw ForbiddenException("동아리장만 할 수 있습니다.", "NOT_LEADER")
        }
        return membership
    }

    /**
     * 세분 권한 게이트 — 동아리장은 전권 통과, STAFF는 해당 PermissionType을 부여받았을 때만 통과.
     * 게시판·자료는 별도의 coarse 게이트(memberRole != MEMBER)를 쓰고, 일정·가입승인·회원기수·동아리설정이 여기에 해당.
     */
    @Transactional(readOnly = true)
    fun requirePermission(userId: Long, type: PermissionType): ClubMember {
        val membership = currentMembership(userId)
        when (membership.memberRole) {
            MemberRole.LEADER -> return membership
            MemberRole.STAFF -> {
                val profile = adminProfileRepository.findByClubMemberId(membership.id)
                if (profile != null && adminPermissionRepository.findByAdminProfileIdAndPermissionType(profile.id, type) != null) {
                    return membership
                }
            }
            else -> Unit
        }
        throw ForbiddenException("이 작업 권한이 없습니다.", "NO_PERMISSION")
    }

    /** 내 세분 권한 집합(/me 노출용). 동아리장=전권, STAFF=부여된 것, 일반=없음. */
    @Transactional(readOnly = true)
    fun permissionsOf(membership: ClubMember): List<PermissionType> = when (membership.memberRole) {
        MemberRole.LEADER -> PermissionType.entries.toList()
        MemberRole.STAFF -> {
            val profile = adminProfileRepository.findByClubMemberId(membership.id)
            if (profile == null) emptyList()
            else adminPermissionRepository.findByAdminProfileIdIn(listOf(profile.id)).map { it.permissionType }
        }
        else -> emptyList()
    }

    /**
     * 44 동아리별 표시 이름 배치 해석 — userId → (club_members.displayName 우선, 없으면 users.nickname,
     * 둘 다 없으면 '탈퇴한 사용자'). 이미 ClubMember를 로드한 지점은 `member.displayName ?: nickname`을
     * 직접 쓰고, users만 배치 조회하던 지점(일정 호스트·자료 업로더 등)이 이 헬퍼로 오버라이드를 반영한다.
     */
    @Transactional(readOnly = true)
    fun displayNamesFor(clubId: Long, userIds: Collection<Long>): Map<Long, String> {
        if (userIds.isEmpty()) return emptyMap()
        val ids = userIds.toSet()
        val overrides = clubMemberRepository.findByClubIdAndUserIdIn(clubId, ids)
            .mapNotNull { m -> m.displayName?.let { m.userId to it } }
            .toMap()
        val nicknames = userRepository.findAllById(ids).associate { it.id to it.nickname }
        return ids.associateWith { overrides[it] ?: nicknames[it] ?: "탈퇴한 사용자" }
    }
}
