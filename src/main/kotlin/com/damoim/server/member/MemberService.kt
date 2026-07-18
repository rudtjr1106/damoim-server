package com.damoim.server.member

import com.damoim.server.club.MembershipService
import com.damoim.server.common.BadRequestException
import com.damoim.server.common.NotFoundException
import com.damoim.server.common.TimeLabels
import com.damoim.server.common.UnauthorizedException
import com.damoim.server.domain.entity.ClubMember
import com.damoim.server.domain.entity.User
import com.damoim.server.domain.enums.ApplicantStatus
import com.damoim.server.domain.enums.MemberRole
import com.damoim.server.domain.enums.PermissionType
import com.damoim.server.domain.enums.MemberStatus
import com.damoim.server.domain.repository.AdminProfileRepository
import com.damoim.server.domain.repository.BoardPostRepository
import com.damoim.server.domain.repository.ClubMemberRepository
import com.damoim.server.domain.repository.CohortRepository
import com.damoim.server.domain.repository.CommentRepository
import com.damoim.server.domain.repository.EventApplicationRepository
import com.damoim.server.domain.repository.UserRepository
import com.damoim.server.storage.StorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 회원 명부·상세·본인 조회 + 운영진 회원 관리(기수 변경/역할 변경/내보내기).
 *
 * 인가는 [MembershipService]에 위임한다: 조회는 활성 회원(currentMembership),
 * 관리 액션은 동아리장(requireLeader). 대상 회원은 항상 요청자의 활성 동아리 스코프로
 * 재검증해 **다른 동아리 회원을 조작하는 IDOR을 차단**한다.
 */
@Service
class MemberService(
    private val membership: MembershipService,
    private val clubMemberRepository: ClubMemberRepository,
    private val cohortRepository: CohortRepository,
    private val userRepository: UserRepository,
    private val boardPostRepository: BoardPostRepository,
    private val commentRepository: CommentRepository,
    private val eventApplicationRepository: EventApplicationRepository,
    private val adminProfileRepository: AdminProfileRepository,
    private val storageService: StorageService,
) {
    /** 프로필 사진 URL — 내부 업로드 키가 있으면 presigned view, 없으면 외부(카카오) URL(프로필 응답과 동일 우선순위). */
    private fun imageUrlOf(u: User?): String? =
        u?.let { it.profileImageKey?.let(storageService::presignView) ?: it.profileImageUrl }

    /** 16/17 명부 — 활동+휴면 전원. LEADER→STAFF→MEMBER, 가입순 정렬. */
    @Transactional(readOnly = true)
    fun list(userId: Long): List<MemberResponse> {
        val me = membership.currentMembership(userId)
        val clubId = me.clubId
        val viewerIsAdmin = me.memberRole != MemberRole.MEMBER
        val members = clubMemberRepository.findByClubId(clubId)
        // 이름·이메일 배치 조회(N+1 제거)
        val users = userRepository.findAllById(members.map { it.userId }).associateBy { it.id }
        return members
            .sortedWith(compareBy({ rolePriority(it.memberRole) }, { it.joinedAt }, { it.id }))
            .map { m ->
                val u = users[m.userId]
                val isMe = m.userId == userId
                toResponse(m, u?.nickname ?: "탈퇴한 사용자", visibleEmail(u?.email, isMe, viewerIsAdmin), isMe, imageUrlOf(u))
            }
    }

    /** 20 내 명부 정보. */
    @Transactional(readOnly = true)
    fun myMember(userId: Long): MemberResponse {
        val member = membership.currentMembership(userId)
        val user = userRepository.findById(userId).orElseThrow { UnauthorizedException() }
        return toResponse(
            member, user.nickname, user.email.orEmpty(), isMe = true, imageUrl = imageUrlOf(user),
            permissions = membership.permissionsOf(member).map { it.name },
        )
    }

    /** 18 회원 상세 — 활동 요약은 실집계(게시글·이벤트·최근 활동). */
    @Transactional(readOnly = true)
    fun detail(userId: Long, memberId: Long): MemberDetailResponse {
        val me = membership.currentMembership(userId)
        val clubId = me.clubId
        val viewerIsAdmin = me.memberRole != MemberRole.MEMBER
        val member = loadMemberInClub(memberId, clubId)
        val isMe = member.userId == userId
        val user = userRepository.findById(member.userId).orElse(null)
        val name = user?.nickname ?: "탈퇴한 사용자"
        val cohortLabel = member.cohortId?.let { cohortRepository.findById(it).orElse(null)?.label } ?: ""

        val postCount = boardPostRepository
            .countByClubIdAndAuthorIdAndDeletedAtIsNull(clubId, member.userId).toInt()
        val eventCount = eventApplicationRepository
            .countAppliedInClub(member.userId, clubId, ApplicantStatus.APPLIED).toInt()
        val lastActive = listOfNotNull(
            boardPostRepository.latestPostAt(clubId, member.userId),
            commentRepository.latestCommentAt(clubId, member.userId),
        ).maxOrNull()

        return MemberDetailResponse(
            member = toResponse(member, name, visibleEmail(user?.email, isMe, viewerIsAdmin), isMe, imageUrlOf(user)),
            cohortLabel = cohortLabel,
            postCount = postCount,
            eventCount = eventCount,
            lastActiveLabel = lastActive?.let { TimeLabels.ago(it) } ?: "활동 없음",
        )
    }

    /** 42 기수 변경(LEADER). 대상 기수도 같은 동아리 소속인지 검증. 카운트는 COUNT 파생이라 별도 조정 없음. */
    @Transactional
    fun changeCohort(userId: Long, memberId: Long, cohortId: Long) {
        val clubId = membership.requirePermission(userId, PermissionType.MEMBER_MANAGE).clubId
        val member = loadMemberInClub(memberId, clubId)
        val cohort = cohortRepository.findById(cohortId)
            .orElseThrow { NotFoundException("기수를 찾을 수 없습니다.") }
        if (cohort.clubId != clubId) throw NotFoundException("기수를 찾을 수 없습니다.")
        member.cohortId = cohortId
        clubMemberRepository.save(member)
    }

    /** 18 역할 변경(LEADER). STAFF↔MEMBER 만. 동아리장(=본인) 역할은 변경 불가. */
    @Transactional
    fun changeRole(userId: Long, memberId: Long, roleStr: String) {
        val clubId = membership.requireLeader(userId).clubId
        val target = when (roleStr.trim().uppercase()) {
            "STAFF" -> MemberRole.STAFF
            "MEMBER" -> MemberRole.MEMBER
            else -> throw BadRequestException("역할은 STAFF 또는 MEMBER여야 합니다.", "INVALID_ROLE")
        }
        val member = loadMemberInClub(memberId, clubId)
        // 동아리장 등급은 위임(권한 이양)으로만 바뀐다 — 여기서 강등/자기변경 금지
        if (member.memberRole == MemberRole.LEADER || member.userId == userId) {
            throw BadRequestException("동아리장의 역할은 변경할 수 없습니다.", "CANNOT_CHANGE_LEADER")
        }
        member.memberRole = target
        clubMemberRepository.save(member)
        // 일반 회원으로 강등 시 운영진 프로필/권한 정리(불변식: admin_profile ⟺ STAFF, 권한 부활 방지)
        if (target == MemberRole.MEMBER) {
            adminProfileRepository.findByClubMemberId(member.id)?.let { adminProfileRepository.delete(it) }
        }
    }

    /** 43 내보내기(LEADER). 본인·동아리장 제외. 명부 행 삭제 + 내보낸 회원 활성 동아리 재지정. */
    @Transactional
    fun remove(userId: Long, memberId: Long) {
        val clubId = membership.requirePermission(userId, PermissionType.MEMBER_MANAGE).clubId
        val member = loadMemberInClub(memberId, clubId)
        if (member.memberRole == MemberRole.LEADER || member.userId == userId) {
            throw BadRequestException("동아리장은 내보낼 수 없습니다.", "CANNOT_REMOVE_LEADER")
        }
        val removedUserId = member.userId
        clubMemberRepository.delete(member)
        // 내보낸 회원의 세션 활성 동아리가 이 동아리였다면 다른 소속으로/없으면 null 재지정
        userRepository.findById(removedUserId).ifPresent { u ->
            if (u.activeClubId == clubId) {
                u.activeClubId = clubMemberRepository
                    .findByUserIdAndStatus(removedUserId, MemberStatus.ACTIVE)
                    .firstOrNull { it.clubId != clubId }?.clubId
                userRepository.save(u)
            }
        }
    }

    /**
     * 동아리장 위임(권한 이양) — 현재 동아리장만. 대상을 LEADER로 승격하고 본인은 STAFF(운영진)로 강등한다.
     * 동아리장 유일성(정확히 1명)을 한 트랜잭션에서 원자적으로 유지 → leaveClub의 "위임 후 탈퇴" 경로를 완성.
     */
    @Transactional
    fun transferLeadership(userId: Long, memberId: Long) {
        val leader = membership.requireLeader(userId)
        val target = loadMemberInClub(memberId, leader.clubId)
        if (target.userId == userId) {
            throw BadRequestException("이미 동아리장입니다.", "ALREADY_LEADER")
        }
        if (target.status != MemberStatus.ACTIVE) {
            throw BadRequestException("활동 중인 회원에게만 위임할 수 있어요.", "TARGET_NOT_ACTIVE")
        }
        target.memberRole = MemberRole.LEADER
        leader.memberRole = MemberRole.STAFF
        clubMemberRepository.save(target)
        clubMemberRepository.save(leader)
        // 새 동아리장은 운영진 프로필이 필요 없다(있으면 제거 — 재이양 시 권한 부활 방지)
        adminProfileRepository.findByClubMemberId(target.id)?.let { adminProfileRepository.delete(it) }
    }

    /** 대상 회원이 요청자 활성 동아리 소속인지 확인(IDOR 차단). 아니면 존재 노출 없이 404. */
    private fun loadMemberInClub(memberId: Long, clubId: Long): ClubMember {
        val member = clubMemberRepository.findById(memberId)
            .orElseThrow { NotFoundException("회원을 찾을 수 없습니다.") }
        if (member.clubId != clubId) throw NotFoundException("회원을 찾을 수 없습니다.")
        return member
    }

    private fun toResponse(m: ClubMember, name: String, email: String, isMe: Boolean, imageUrl: String? = null, permissions: List<String> = emptyList()) = MemberResponse(
        id = m.id,
        name = name,
        initials = initialsOf(name),
        cohortId = m.cohortId ?: 0,
        role = m.memberRole.name,
        status = m.status.name,
        email = email,
        joinedLabel = TimeLabels.date(m.joinedAt),
        isMe = isMe,
        profileImageUrl = imageUrl,
        permissions = permissions,
    )

    /**
     * 이메일(PII)은 **본인 또는 운영진(LEADER/STAFF)** 에게만 노출한다. 일반 회원에게는 타인 이메일을
     * 빈 문자열로 마스킹 — 명부 1회 호출로 전원 이메일을 수확하는 것을 차단(보안 리뷰 반영).
     */
    private fun visibleEmail(raw: String?, isMe: Boolean, viewerIsAdmin: Boolean): String =
        if (isMe || viewerIsAdmin) raw.orEmpty() else ""

    /** 한글 이름 끝 2글자(3글자 이상). 클라 initialsOf와 동일 규칙. */
    private fun initialsOf(name: String) = if (name.length >= 3) name.takeLast(2) else name

    private fun rolePriority(role: MemberRole) = when (role) {
        MemberRole.LEADER -> 0
        MemberRole.STAFF -> 1
        MemberRole.MEMBER -> 2
    }
}
