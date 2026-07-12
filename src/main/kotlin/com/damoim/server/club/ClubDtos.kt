package com.damoim.server.club

import com.damoim.server.domain.entity.Club
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// ── 요청 ──
data class CreateClubRequest(
    @field:NotBlank(message = "동아리 이름은 필수입니다.")
    @field:Size(max = 100, message = "이름은 100자 이하여야 합니다.")
    val name: String,
    @field:NotBlank(message = "카테고리는 필수입니다.")
    @field:Size(max = 50)
    val category: String,
    @field:Size(max = 2000, message = "소개는 2000자 이하여야 합니다.")
    val intro: String = "",
)

// ── 응답 ──
data class ClubResponse(
    val id: Long,
    val name: String,
    val category: String,
    val description: String,
    val joinCode: String?,
    val joinCodeActive: Boolean,
    val memberCount: Int,
    val emblemColor: Long,
) {
    companion object {
        /** joinCode는 운영 비밀 — LEADER에게만 노출(includeJoinCode=false면 감춤). */
        fun from(c: Club, memberCount: Int, includeJoinCode: Boolean = true) = ClubResponse(
            id = c.id,
            name = c.name,
            category = c.category,
            description = c.description,
            joinCode = c.joinCode?.takeIf { c.joinCodeActive && includeJoinCode },
            joinCodeActive = c.joinCodeActive,
            memberCount = memberCount,
            emblemColor = c.emblemColor,
        )
    }
}

/** 가입 코드 카드(08). 비활성 시 joinCode=null. */
data class JoinCodeResponse(val joinCode: String?, val joinCodeActive: Boolean)

data class CohortResponse(val id: Long, val label: String, val short: String, val memberCount: Int)

// ── 홈 요약(05/06) ──
data class HomeSummaryResponse(
    val role: String,                 // LEADER / MEMBER (member_role에서 파생)
    val clubName: String,
    val memberName: String,
    val stats: List<HomeStatDto>,
    val alert: HomeAlertDto?,
    val schedules: List<UpcomingScheduleDto>,   // F 그룹 도입 전까지 빈 배열
    val boardPreviews: List<BoardPreviewDto>,    // C 그룹 도입 전까지 빈 배열
    val hasUnreadNotification: Boolean,
)

data class HomeStatDto(val value: String, val label: String)
data class HomeAlertDto(val title: String, val subtitle: String, val kind: String, val badge: String? = null)
data class UpcomingScheduleDto(
    val id: Long,
    val dday: String,
    val date: String,
    val title: String,
    val subtitle: String,
    val primary: Boolean,
)
data class BoardPreviewDto(val category: String, val title: String, val commentCount: Int)
