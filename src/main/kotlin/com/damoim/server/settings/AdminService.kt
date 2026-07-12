package com.damoim.server.settings

import com.damoim.server.club.MembershipService
import com.damoim.server.common.BadRequestException
import com.damoim.server.common.NotFoundException
import com.damoim.server.domain.entity.AdminPermission
import com.damoim.server.domain.entity.AdminProfile
import com.damoim.server.domain.entity.ClubMember
import com.damoim.server.domain.enums.MemberRole
import com.damoim.server.domain.enums.MemberStatus
import com.damoim.server.domain.enums.PermissionType
import com.damoim.server.domain.repository.AdminPermissionRepository
import com.damoim.server.domain.repository.AdminProfileRepository
import com.damoim.server.domain.repository.ClubMemberRepository
import com.damoim.server.domain.repository.CohortRepository
import com.damoim.server.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 운영진 권한(30/64) — 전부 동아리장 전용(클라는 게이팅 안 하므로 서버가 강제).
 * 운영진 = member_role==STAFF. 직함/권한은 admin_profiles·admin_permissions로 확장.
 * addAdmin/removeAdmin은 club_members.member_role(STAFF↔MEMBER)을 함께 변경(단일 진실원).
 */
@Service
class AdminService(
    private val membership: MembershipService,
    private val clubMemberRepository: ClubMemberRepository,
    private val cohortRepository: CohortRepository,
    private val userRepository: UserRepository,
    private val adminProfileRepository: AdminProfileRepository,
    private val adminPermissionRepository: AdminPermissionRepository,
) {
    /** 30 운영진 목록(STAFF + 직함 + 권한). */
    @Transactional(readOnly = true)
    fun list(userId: Long): List<AdminMemberResponse> {
        val clubId = membership.requireLeader(userId).clubId
        val staff = clubMemberRepository.findByClubId(clubId).filter { it.memberRole == MemberRole.STAFF }
        if (staff.isEmpty()) return emptyList()
        val users = userRepository.findAllById(staff.map { it.userId }).associate { it.id to it.nickname }
        val cohorts = cohortRepository.findAllById(staff.mapNotNull { it.cohortId }.distinct())
            .associate { it.id to it.short }
        val profiles = adminProfileRepository.findByClubMemberIdIn(staff.map { it.id }).associateBy { it.clubMemberId }
        val perms = adminPermissionRepository.findByAdminProfileIdIn(profiles.values.map { it.id })
            .groupBy { it.adminProfileId }
        return staff.map { m ->
            val name = users[m.userId] ?: "탈퇴한 사용자"
            val profile = profiles[m.id]
            AdminMemberResponse(
                userId = m.userId,
                name = name,
                initials = initialsOf(name),
                cohortLabel = m.cohortId?.let { cohorts[it] } ?: "",
                title = profile?.title ?: DEFAULT_TITLE,
                permissions = profile?.let { perms[it.id].orEmpty().map { p -> p.permissionType.name } } ?: emptyList(),
            )
        }
    }

    /** 30 운영진 추가 후보 — 본인 제외, 일반 회원(ACTIVE)만. */
    @Transactional(readOnly = true)
    fun assignable(userId: Long): List<AdminCandidateResponse> {
        val clubId = membership.requireLeader(userId).clubId
        val members = clubMemberRepository.findByClubId(clubId)
            .filter { it.memberRole == MemberRole.MEMBER && it.status == MemberStatus.ACTIVE && it.userId != userId }
        if (members.isEmpty()) return emptyList()
        val users = userRepository.findAllById(members.map { it.userId }).associate { it.id to it.nickname }
        val cohorts = cohortRepository.findAllById(members.mapNotNull { it.cohortId }.distinct())
            .associate { it.id to it.short }
        return members.map { m ->
            val name = users[m.userId] ?: "탈퇴한 사용자"
            AdminCandidateResponse(
                memberId = m.id,
                name = name,
                initials = initialsOf(name),
                cohortLabel = m.cohortId?.let { cohorts[it] } ?: "",
            )
        }
    }

    /** 30 운영진 지정 — MEMBER→STAFF 승격 + 직함 + 기본 권한(NOTICE_WRITE). */
    @Transactional
    fun addAdmin(userId: Long, req: AddAdminRequest) {
        val clubId = membership.requireLeader(userId).clubId
        val member = loadMemberInClub(req.memberId, clubId)
        if (member.memberRole == MemberRole.LEADER) {
            throw BadRequestException("동아리장은 운영진으로 지정할 수 없습니다.", "CANNOT_ADMIN_LEADER")
        }
        if (member.status != MemberStatus.ACTIVE) {
            throw BadRequestException("활동 중인 회원만 운영진으로 지정할 수 있어요.", "MEMBER_NOT_ACTIVE")
        }
        member.memberRole = MemberRole.STAFF
        clubMemberRepository.save(member)
        val profile = ensureProfile(member).apply { title = req.title.trim() }
        adminProfileRepository.save(profile)
        if (adminPermissionRepository.findByAdminProfileIdAndPermissionType(profile.id, PermissionType.NOTICE_WRITE) == null) {
            adminPermissionRepository.save(
                AdminPermission().apply { adminProfileId = profile.id; permissionType = PermissionType.NOTICE_WRITE },
            )
        }
    }

    /** 64 운영진 해제 — STAFF→MEMBER 강등 + 프로필/권한 삭제(CASCADE). */
    @Transactional
    fun removeAdmin(userId: Long, targetUserId: Long) {
        val clubId = membership.requireLeader(userId).clubId
        val member = requireStaff(clubId, targetUserId)
        member.memberRole = MemberRole.MEMBER
        clubMemberRepository.save(member)
        adminProfileRepository.findByClubMemberId(member.id)?.let { adminProfileRepository.delete(it) }
    }

    /** 64 직함 변경. */
    @Transactional
    fun changeTitle(userId: Long, targetUserId: Long, title: String) {
        val clubId = membership.requireLeader(userId).clubId
        val member = requireStaff(clubId, targetUserId)
        val profile = ensureProfile(member).apply { this.title = title.trim() }
        adminProfileRepository.save(profile)
    }

    /** 30 권한 토글(insert/delete). */
    @Transactional
    fun togglePermission(userId: Long, targetUserId: Long, typeStr: String) {
        val clubId = membership.requireLeader(userId).clubId
        val member = requireStaff(clubId, targetUserId)
        val type = parsePermission(typeStr)
        val profile = ensureProfile(member)
        val existing = adminPermissionRepository.findByAdminProfileIdAndPermissionType(profile.id, type)
        if (existing != null) {
            adminPermissionRepository.delete(existing)
        } else {
            adminPermissionRepository.save(
                AdminPermission().apply { adminProfileId = profile.id; permissionType = type },
            )
        }
    }

    // ── 내부 ──

    private fun ensureProfile(member: ClubMember): AdminProfile =
        adminProfileRepository.findByClubMemberId(member.id)
            ?: adminProfileRepository.save(AdminProfile().apply { clubMemberId = member.id; title = DEFAULT_TITLE })

    /** 대상이 이 동아리의 STAFF(운영진)인지 확인. */
    private fun requireStaff(clubId: Long, targetUserId: Long): ClubMember {
        val member = clubMemberRepository.findByClubIdAndUserId(clubId, targetUserId)
            ?: throw NotFoundException("회원을 찾을 수 없습니다.")
        if (member.memberRole != MemberRole.STAFF) {
            throw BadRequestException("운영진이 아닙니다.", "NOT_AN_ADMIN")
        }
        return member
    }

    private fun loadMemberInClub(memberId: Long, clubId: Long): ClubMember {
        val member = clubMemberRepository.findById(memberId)
            .orElseThrow { NotFoundException("회원을 찾을 수 없습니다.") }
        if (member.clubId != clubId) throw NotFoundException("회원을 찾을 수 없습니다.")
        return member
    }

    private fun parsePermission(v: String): PermissionType =
        runCatching { PermissionType.valueOf(v.trim().uppercase()) }.getOrElse {
            throw BadRequestException("권한 종류가 올바르지 않습니다.", "INVALID_PERMISSION")
        }

    private fun initialsOf(name: String) = if (name.length >= 3) name.takeLast(2) else name

    private companion object {
        const val DEFAULT_TITLE = "운영진"
    }
}
