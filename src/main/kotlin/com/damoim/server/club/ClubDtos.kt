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

// ── 요청: 동아리 정보 수정(08) — LEADER ──
/** 대표 이미지 업로드 URL 요청(1단계). presigned PUT 후 imageKey를 PATCH /me에 전달. */
data class ClubImageUploadRequest(
    @field:Size(max = 255) val fileName: String? = null,
    @field:Size(max = 255) val contentType: String? = null,
    @field:jakarta.validation.constraints.Min(value = 1, message = "파일 크기가 올바르지 않습니다.")
    val sizeBytes: Long,
)

/** 대표 이미지 업로드 URL 응답 — 클라가 이 URL로 S3에 직접 PUT 후 imageKey를 수정 요청에 전달. */
data class ClubImageUploadResponse(
    val uploadUrl: String,
    val storageKey: String,
    val expiresInSeconds: Long,
)

/** 동아리 정보 수정(08). null인 필드는 변경하지 않는다(부분 수정). */
data class UpdateClubRequest(
    @field:Size(max = 100, message = "이름은 100자 이하여야 합니다.")
    val name: String? = null,
    @field:Size(max = 2000, message = "소개는 2000자 이하여야 합니다.")
    val intro: String? = null,
    /** 앱에서 presigned PUT으로 올린 대표 이미지의 S3 키. 서버가 소유권 검증 후 저장. */
    @field:Size(max = 1024)
    val imageKey: String? = null,
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
    val imageUrl: String?,            // 대표 이미지 presigned view URL(없으면 null → 이니셜 로고)
) {
    companion object {
        /** joinCode는 운영 비밀 — LEADER에게만 노출(includeJoinCode=false면 감춤). */
        fun from(c: Club, memberCount: Int, includeJoinCode: Boolean = true, imageUrl: String? = null) = ClubResponse(
            id = c.id,
            name = c.name,
            category = c.category,
            description = c.description,
            joinCode = c.joinCode?.takeIf { c.joinCodeActive && includeJoinCode },
            joinCodeActive = c.joinCodeActive,
            memberCount = memberCount,
            emblemColor = c.emblemColor,
            imageUrl = imageUrl,
        )
    }
}

/** 가입 코드 카드(08). 비활성 시 joinCode=null. */
data class JoinCodeResponse(val joinCode: String?, val joinCodeActive: Boolean)

data class CohortResponse(val id: Long, val label: String, val short: String, val memberCount: Int)

// ── 기수 추가(44) / 이름 변경(19) ──
// short = 약칭("26기", 필수), label = 정식 표기("2026학년 1기 (26기)"). label 비면 short로 폴백.
data class CohortCreateRequest(
    @field:NotBlank(message = "기수 번호는 필수입니다.")
    @field:Size(max = 30, message = "기수 약칭은 30자 이하여야 합니다.")
    val short: String,
    @field:Size(max = 80, message = "표시 이름은 80자 이하여야 합니다.")
    val label: String = "",
)

data class CohortRenameRequest(
    @field:NotBlank(message = "기수 번호는 필수입니다.")
    @field:Size(max = 30, message = "기수 약칭은 30자 이하여야 합니다.")
    val short: String,
    @field:Size(max = 80, message = "표시 이름은 80자 이하여야 합니다.")
    val label: String = "",
)

// ── 멀티 동아리 전환(33) ──
/** 내가 속한 동아리 + 그 동아리에서의 세션 역할(ClubRole: LEADER/MEMBER). */
data class ClubMembershipResponse(val club: ClubResponse, val role: String)

/** 활성 동아리 전환 요청 — 대상은 내가 ACTIVE 회원인 동아리만 허용. */
data class SwitchClubRequest(val clubId: Long)

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
data class BoardPreviewDto(val id: Long, val category: String, val title: String, val commentCount: Int, val isPinned: Boolean = false)
