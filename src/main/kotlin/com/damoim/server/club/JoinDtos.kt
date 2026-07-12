package com.damoim.server.club

import com.damoim.server.domain.entity.Club
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 가입 신청 결과에 실을 최소 동아리 정보(승인 전 비회원 노출 최소화 — description·회원수·코드 제외). */
data class ClubSummary(val id: Long, val name: String, val category: String, val emblemColor: Long) {
    companion object {
        fun from(c: Club) = ClubSummary(c.id, c.name, c.category, c.emblemColor)
    }
}

// ── 가입 코드 제출(03) ──
data class JoinCodeRequest(
    @field:NotBlank(message = "가입 코드는 필수입니다.")
    @field:Size(max = 20, message = "가입 코드가 올바르지 않습니다.")
    val code: String,
)

/** 가입 신청 결과(04 대기 / 38 거절). status = PENDING/APPROVED/REJECTED. */
data class JoinResultResponse(
    val club: ClubSummary,
    val status: String,
    val rejectionReason: String? = null,
)

// ── 가입 신청 관리(09) ──
data class ApplicantsBoardResponse(
    val pending: List<ApplicantResponse>,
    val processed: List<ProcessedApplicantResponse>,
)

data class ApplicantResponse(
    val id: Long,                 // join_application id
    val name: String,
    val initial: String,          // 이름 파생
    val desiredGisu: String?,     // 희망 기수 short (없을 수 있음)
    val appliedDate: String,      // "6.03" (created_at 파생)
    val timeAgo: String,          // "방금 전"
    val message: String?,
)

data class ProcessedApplicantResponse(
    val applicant: ApplicantResponse,
    val approved: Boolean,
    val decidedLabel: String,     // "방금 전" (decided_at 파생)
)

/** 승인/거절 요청. approve=false일 때 rejectionReason 선택. */
data class DecideRequest(
    val approve: Boolean,
    @field:Size(max = 500)
    val rejectionReason: String? = null,
)
