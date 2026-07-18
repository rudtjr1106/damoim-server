package com.damoim.server.club

import com.damoim.server.common.BadRequestException
import com.damoim.server.common.ConflictException
import com.damoim.server.common.ForbiddenException
import com.damoim.server.common.NotFoundException
import com.damoim.server.common.TimeLabels
import com.damoim.server.common.UnauthorizedException
import com.damoim.server.domain.entity.Club
import com.damoim.server.domain.entity.ClubMember
import com.damoim.server.domain.entity.Cohort
import com.damoim.server.domain.enums.JoinStatus
import com.damoim.server.domain.enums.MemberRole
import com.damoim.server.domain.enums.MemberStatus
import com.damoim.server.domain.enums.PermissionType
import com.damoim.server.domain.repository.BoardPostRepository
import com.damoim.server.domain.repository.ClubMemberRepository
import com.damoim.server.domain.repository.ClubRepository
import com.damoim.server.domain.repository.CohortRepository
import com.damoim.server.domain.repository.CommentRepository
import com.damoim.server.domain.repository.JoinApplicationRepository
import com.damoim.server.domain.repository.NotificationRepository
import com.damoim.server.domain.repository.ScheduleRepository
import com.damoim.server.domain.repository.UserRepository
import com.damoim.server.storage.StorageKeys
import com.damoim.server.storage.StorageService
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ClubService(
    private val clubRepository: ClubRepository,
    private val clubMemberRepository: ClubMemberRepository,
    private val cohortRepository: CohortRepository,
    private val joinApplicationRepository: JoinApplicationRepository,
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
    private val membership: MembershipService,
    private val storageService: StorageService,
    private val scheduleRepository: ScheduleRepository,
    private val boardPostRepository: BoardPostRepository,
    private val commentRepository: CommentRepository,
) {
    /** 대표 이미지 키가 있으면 presigned view URL로 파생(프로필 사진과 동일 패턴). */
    private fun imageUrlOf(club: Club): String? = club.imageKey?.let { storageService.presignView(it) }
    /** 동아리 생성 — 생성자는 LEADER로 자동 가입, '1기' 기본 기수 생성, 활성 동아리 지정. */
    @Transactional
    fun create(userId: Long, req: CreateClubRequest): ClubResponse {
        val user = userRepository.findById(userId).orElseThrow { UnauthorizedException() }
        val club = clubRepository.save(
            Club().apply {
                name = req.name.trim()
                category = req.category.trim()
                description = req.intro.trim()
                joinCode = uniqueJoinCode()
                joinCodeActive = true
            },
        )
        val cohort = cohortRepository.save(
            Cohort().apply {
                clubId = club.id
                label = "1기"
                short = "1기"
            },
        )
        clubMemberRepository.save(
            ClubMember().apply {
                clubId = club.id
                this.userId = userId
                memberRole = MemberRole.LEADER
                status = MemberStatus.ACTIVE
                cohortId = cohort.id
                joinedAt = Instant.now()
            },
        )
        user.activeClubId = club.id
        userRepository.save(user)
        return ClubResponse.from(club, memberCount = 1, imageUrl = imageUrlOf(club))
    }

    @Transactional(readOnly = true)
    fun myClub(userId: Long): ClubResponse {
        val member = membership.currentMembership(userId)
        val club = clubRepository.findById(member.clubId)
            .orElseThrow { NotFoundException("동아리를 찾을 수 없습니다.") }
        val isLeader = member.memberRole == MemberRole.LEADER
        return ClubResponse.from(club, activeMemberCount(member.clubId), includeJoinCode = isLeader, imageUrl = imageUrlOf(club))
    }

    /** 대표 이미지 업로드 URL 발급(08) — LEADER. 클라가 이 URL로 S3에 직접 PUT 후 imageKey를 PATCH /me에 전달. */
    @Transactional(readOnly = true)
    fun createImageUploadUrl(userId: Long, req: ClubImageUploadRequest): ClubImageUploadResponse {
        val clubId = membership.requirePermission(userId, PermissionType.CLUB_SETTINGS).clubId
        if (req.sizeBytes > CLUB_IMAGE_MAX_BYTES) throw BadRequestException("이미지가 너무 큽니다.", "FILE_TOO_LARGE")
        val up = storageService.presignUpload(
            StorageKeys.forClub(clubId, req.fileName?.takeIf { it.isNotBlank() } ?: "logo"),
            req.contentType,
        )
        return ClubImageUploadResponse(up.url, up.key, up.expiresInSeconds)
    }

    /** 동아리 정보 수정(08) — LEADER. null 필드는 변경하지 않음. 이미지 키는 소유권·실오브젝트 검증 후 저장. */
    @Transactional
    fun updateClub(userId: Long, req: UpdateClubRequest): ClubResponse {
        val member = membership.requirePermission(userId, PermissionType.CLUB_SETTINGS)
        val club = clubRepository.findById(member.clubId)
            .orElseThrow { NotFoundException("동아리를 찾을 수 없습니다.") }
        req.name?.trim()?.takeIf { it.isNotEmpty() }?.let { club.name = it }
        req.intro?.trim()?.let { club.description = it }
        req.imageKey?.takeIf { it.isNotBlank() }?.let { key ->
            if (!key.startsWith("clubs/${club.id}/")) {
                throw ForbiddenException("잘못된 업로드 키입니다.", "INVALID_STORAGE_KEY")
            }
            if (storageService.verifiesSize && storageService.objectSizeOrNull(key) == null) {
                throw BadRequestException("업로드가 완료되지 않았습니다.", "UPLOAD_INCOMPLETE")
            }
            club.imageKey = key
        }
        clubRepository.save(club)
        return ClubResponse.from(club, activeMemberCount(club.id), includeJoinCode = true, imageUrl = imageUrlOf(club))
    }

    @Transactional(readOnly = true)
    fun home(userId: Long): HomeSummaryResponse {
        val member = membership.currentMembership(userId)
        val clubId = member.clubId
        val club = clubRepository.findById(clubId).orElseThrow { NotFoundException("동아리를 찾을 수 없습니다.") }
        val user = userRepository.findById(userId).orElseThrow { UnauthorizedException() }
        val isLeader = member.memberRole == MemberRole.LEADER
        val myCohortShort = member.cohortId?.let { cohortRepository.findById(it).orElse(null)?.short } ?: "-"
        val memberCount = activeMemberCount(clubId)

        // 다가오는 일정 3건 — 아직 안 끝난 것(종료일 기준, 진행 중 다일정 포함), 날짜순. 첫 카드가 primary(강조).
        val today = TimeLabels.todayKst()
        val upcoming = scheduleRepository.findByClubIdOrderByScheduleDateAscCreatedAtAsc(clubId)
            .filter { !(it.endDate ?: it.scheduleDate).isBefore(today) }
            .take(3)
        val schedules = upcoming.mapIndexed { i, s ->
            UpcomingScheduleDto(
                id = s.id,
                dday = TimeLabels.ddayFromDate(s.scheduleDate, today),
                date = TimeLabels.homeDate(s.scheduleDate),
                title = s.title,
                subtitle = buildString {
                    append(TimeLabels.koreanTime(s.startHour.toInt(), s.startMinute.toInt()))
                    if (s.location.isNotBlank()) append(" · ${s.location}")
                },
                primary = i == 0,
            )
        }

        // 게시판 미리보기 3건 — 필독 먼저(isPinned desc)·최신순, 댓글 수 일괄 조회(N+1 방지).
        val recentPosts = boardPostRepository.findFeed(clubId, null, PageRequest.of(0, 3))
        val commentCounts = if (recentPosts.isEmpty()) {
            emptyMap()
        } else {
            commentRepository.countByPosts(recentPosts.map { it.id })
                .associate { (it[0] as Long) to (it[1] as Long).toInt() }
        }
        val boardPreviews = recentPosts.map {
            BoardPreviewDto(id = it.id, category = it.category.name, title = it.title, commentCount = commentCounts[it.id] ?: 0, isPinned = it.isPinned)
        }

        val alert: HomeAlertDto? = if (isLeader) {
            val pending = joinApplicationRepository
                .findByClubIdAndStatusOrderByCreatedAtDesc(clubId, JoinStatus.PENDING).size
            if (pending > 0) {
                HomeAlertDto("가입 신청 ${pending}건", "승인을 기다리고 있어요", "JOIN_REQUEST")
            } else {
                null
            }
        } else {
            // 일반회원: 가장 가까운 일정 D-day 알림 카드.
            upcoming.firstOrNull()?.let { s ->
                HomeAlertDto(
                    title = s.title,
                    subtitle = "${TimeLabels.midDate(s.scheduleDate)} ${TimeLabels.koreanTime(s.startHour.toInt(), s.startMinute.toInt())}",
                    kind = "SCHEDULE",
                    badge = TimeLabels.ddayFromDate(s.scheduleDate, today),
                )
            }
        }

        return HomeSummaryResponse(
            role = if (isLeader) "LEADER" else "MEMBER",
            clubName = club.name,
            memberName = user.nickname,
            stats = listOf(
                HomeStatDto(memberCount.toString(), "회원"),
                HomeStatDto(myCohortShort, "내 기수"),
            ),
            alert = alert,
            schedules = schedules,
            boardPreviews = boardPreviews,
            hasUnreadNotification = notificationRepository.countUnread(userId, clubId) > 0,
        )
    }

    @Transactional(readOnly = true)
    fun cohorts(userId: Long): List<CohortResponse> {
        // 형제 조회들과 동일하게 ACTIVE 멤버십을 재검증(휴면=권한회수 실효성 통일, 보안 리뷰 반영)
        val clubId = membership.currentMembership(userId).clubId
        return cohortRepository.findByClubIdOrderByCreatedAtAsc(clubId).map {
            CohortResponse(
                it.id,
                it.label,
                it.short,
                clubMemberRepository.countByCohortIdAndStatus(it.id, MemberStatus.ACTIVE).toInt(),
            )
        }
    }

    /** 가입 코드 재발급(LEADER). */
    @Transactional
    fun regenerateJoinCode(userId: Long): JoinCodeResponse {
        val clubId = membership.requirePermission(userId, PermissionType.CLUB_SETTINGS).clubId
        val club = clubRepository.findById(clubId).orElseThrow { NotFoundException("동아리를 찾을 수 없습니다.") }
        club.joinCode = uniqueJoinCode()
        club.joinCodeActive = true
        clubRepository.save(club)
        return JoinCodeResponse(club.joinCode, true)
    }

    /** 가입 코드 비활성화(LEADER). */
    @Transactional
    fun disableJoinCode(userId: Long): JoinCodeResponse {
        val clubId = membership.requirePermission(userId, PermissionType.CLUB_SETTINGS).clubId
        val club = clubRepository.findById(clubId).orElseThrow { NotFoundException("동아리를 찾을 수 없습니다.") }
        club.joinCodeActive = false
        clubRepository.save(club)
        return JoinCodeResponse(null, false)
    }

    // ── E. 기수 관리(19/44) ──

    /** 새 기수 추가(44) — LEADER. 동아리 내 약칭 중복 금지. label 비면 short로 폴백. */
    @Transactional
    fun addCohort(userId: Long, req: CohortCreateRequest): CohortResponse {
        val clubId = membership.requirePermission(userId, PermissionType.MEMBER_MANAGE).clubId
        val short = req.short.trim()
        val label = req.label.trim().ifEmpty { short }
        if (cohortRepository.existsByClubIdAndShort(clubId, short)) {
            throw ConflictException("이미 있는 기수예요.", "COHORT_EXISTS")
        }
        val cohort = cohortRepository.save(
            Cohort().apply {
                this.clubId = clubId
                this.label = label
                this.short = short
            },
        )
        return CohortResponse(cohort.id, cohort.label, cohort.short, memberCount = 0)
    }

    /** 기수 이름 변경(19) — LEADER. 기수는 내 동아리 소속만, 약칭 중복 금지(자기 자신 제외). */
    @Transactional
    fun renameCohort(userId: Long, cohortId: Long, req: CohortRenameRequest) {
        val clubId = membership.requirePermission(userId, PermissionType.MEMBER_MANAGE).clubId
        val cohort = cohortRepository.findById(cohortId)
            .orElseThrow { NotFoundException("기수를 찾을 수 없습니다.") }
        if (cohort.clubId != clubId) throw NotFoundException("기수를 찾을 수 없습니다.")
        val short = req.short.trim()
        val label = req.label.trim().ifEmpty { short }
        cohortRepository.findByClubIdAndShort(clubId, short)?.let {
            if (it.id != cohortId) throw ConflictException("이미 있는 기수예요.", "COHORT_EXISTS")
        }
        cohort.short = short
        cohort.label = label
        cohortRepository.save(cohort)
    }

    // ── E. 멀티 동아리 전환·탈퇴(33/60) ──

    /** 내가 속한 동아리 목록(33). 각 동아리에서의 세션 역할 포함. 가입 코드는 노출하지 않음. */
    @Transactional(readOnly = true)
    fun joinedClubs(userId: Long): List<ClubMembershipResponse> {
        val memberships = clubMemberRepository.findByUserIdAndStatus(userId, MemberStatus.ACTIVE)
        if (memberships.isEmpty()) return emptyList()
        val clubs = clubRepository.findAllById(memberships.map { it.clubId }).associateBy { it.id }
        return memberships.mapNotNull { m ->
            val club = clubs[m.clubId] ?: return@mapNotNull null
            val role = if (m.memberRole == MemberRole.LEADER) "LEADER" else "MEMBER"
            ClubMembershipResponse(
                ClubResponse.from(club, activeMemberCount(club.id), includeJoinCode = false, imageUrl = imageUrlOf(club)),
                role,
            )
        }
    }

    /** 활성 동아리 전환(33) — 내가 ACTIVE 회원인 동아리만. 전환 후 활성 동아리 요약 반환. */
    @Transactional
    fun switchClub(userId: Long, clubId: Long): ClubResponse {
        val member = clubMemberRepository.findByClubIdAndUserId(clubId, userId)
            ?: throw ForbiddenException("가입한 동아리가 아닙니다.", "NOT_A_MEMBER")
        if (member.status != MemberStatus.ACTIVE) {
            throw ForbiddenException("활동 중인 회원이 아닙니다.", "NOT_A_MEMBER")
        }
        val user = userRepository.findById(userId).orElseThrow { UnauthorizedException() }
        user.activeClubId = clubId
        userRepository.save(user)
        val club = clubRepository.findById(clubId)
            .orElseThrow { NotFoundException("동아리를 찾을 수 없습니다.") }
        return ClubResponse.from(
            club,
            activeMemberCount(clubId),
            includeJoinCode = member.memberRole == MemberRole.LEADER,
            imageUrl = imageUrlOf(club),
        )
    }

    /**
     * 현재 활성 동아리 탈퇴(60). 명부 행을 제거하고 다른 소속으로/없으면 null로 활성 동아리 재지정.
     * 동아리장은 다른 회원이 남아 있으면 탈퇴 불가(위임 필요) — 리더 없는 동아리 방지.
     */
    @Transactional
    fun leaveClub(userId: Long) {
        val member = membership.currentMembership(userId)
        val clubId = member.clubId
        val leavingAsLeader = member.memberRole == MemberRole.LEADER
        if (leavingAsLeader &&
            clubMemberRepository.countByClubIdAndStatus(clubId, MemberStatus.ACTIVE) > 1
        ) {
            throw ConflictException(
                "동아리장은 먼저 다른 회원에게 위임한 뒤 탈퇴할 수 있어요.",
                "LEADER_MUST_DELEGATE",
            )
        }
        clubMemberRepository.delete(member)
        if (leavingAsLeader) {
            // 단독 리더 탈퇴 → 리더 없는 동아리가 남는다. 처리 불가한 가입 신청이 쌓이지 않도록
            // 가입 코드를 비활성화해 신규 유입을 차단(좀비 동아리 방지, 보안 리뷰 반영).
            clubRepository.findById(clubId).ifPresent {
                if (it.joinCodeActive) {
                    it.joinCodeActive = false
                    clubRepository.save(it)
                }
            }
        }
        val user = userRepository.findById(userId).orElseThrow { UnauthorizedException() }
        user.activeClubId = clubMemberRepository
            .findByUserIdAndStatus(userId, MemberStatus.ACTIVE)
            .firstOrNull { it.clubId != clubId }?.clubId
        userRepository.save(user)
    }

    private fun activeMemberCount(clubId: Long): Int =
        clubMemberRepository.countByClubIdAndStatus(clubId, MemberStatus.ACTIVE).toInt()

    private fun uniqueJoinCode(): String {
        repeat(10) {
            val code = JoinCodes.generate()
            if (!clubRepository.existsByJoinCodeAndJoinCodeActiveIsTrue(code)) return code
        }
        throw IllegalStateException("가입 코드 생성에 실패했습니다.")
    }

    private companion object {
        const val CLUB_IMAGE_MAX_BYTES = 5L * 1024 * 1024  // 대표 이미지 상한 5MB
    }
}
