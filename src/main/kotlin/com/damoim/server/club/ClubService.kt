package com.damoim.server.club

import com.damoim.server.common.NotFoundException
import com.damoim.server.common.UnauthorizedException
import com.damoim.server.domain.entity.Club
import com.damoim.server.domain.entity.ClubMember
import com.damoim.server.domain.entity.Cohort
import com.damoim.server.domain.enums.JoinStatus
import com.damoim.server.domain.enums.MemberRole
import com.damoim.server.domain.enums.MemberStatus
import com.damoim.server.domain.repository.ClubMemberRepository
import com.damoim.server.domain.repository.ClubRepository
import com.damoim.server.domain.repository.CohortRepository
import com.damoim.server.domain.repository.JoinApplicationRepository
import com.damoim.server.domain.repository.NotificationRepository
import com.damoim.server.domain.repository.UserRepository
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
) {
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
        return ClubResponse.from(club, memberCount = 1)
    }

    @Transactional(readOnly = true)
    fun myClub(userId: Long): ClubResponse {
        val member = membership.currentMembership(userId)
        val club = clubRepository.findById(member.clubId)
            .orElseThrow { NotFoundException("동아리를 찾을 수 없습니다.") }
        val isLeader = member.memberRole == MemberRole.LEADER
        return ClubResponse.from(club, activeMemberCount(member.clubId), includeJoinCode = isLeader)
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

        val alert: HomeAlertDto? = if (isLeader) {
            val pending = joinApplicationRepository
                .findByClubIdAndStatusOrderByCreatedAtDesc(clubId, JoinStatus.PENDING).size
            if (pending > 0) {
                HomeAlertDto("가입 신청 ${pending}건", "승인을 기다리고 있어요", "JOIN_REQUEST")
            } else {
                null
            }
        } else {
            null // 다가오는 일정 알림은 F 그룹 도입 후
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
            schedules = emptyList(),
            boardPreviews = emptyList(),
            hasUnreadNotification = notificationRepository.countUnread(userId) > 0,
        )
    }

    @Transactional(readOnly = true)
    fun cohorts(userId: Long): List<CohortResponse> {
        val clubId = membership.currentClubId(userId)
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
        val clubId = membership.requireLeader(userId).clubId
        val club = clubRepository.findById(clubId).orElseThrow { NotFoundException("동아리를 찾을 수 없습니다.") }
        club.joinCode = uniqueJoinCode()
        club.joinCodeActive = true
        clubRepository.save(club)
        return JoinCodeResponse(club.joinCode, true)
    }

    /** 가입 코드 비활성화(LEADER). */
    @Transactional
    fun disableJoinCode(userId: Long): JoinCodeResponse {
        val clubId = membership.requireLeader(userId).clubId
        val club = clubRepository.findById(clubId).orElseThrow { NotFoundException("동아리를 찾을 수 없습니다.") }
        club.joinCodeActive = false
        clubRepository.save(club)
        return JoinCodeResponse(null, false)
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
}
