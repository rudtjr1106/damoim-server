package com.damoim.server.settings

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// ── 응답 ──

/** 30 운영진 카드 — 회원 + 직함 + 권한 셋. */
data class AdminMemberResponse(
    val userId: Long,
    val name: String,
    val initials: String,
    val cohortLabel: String,          // "23기" (기수 short)
    val title: String,                // "부회장" / "총무"
    val permissions: List<String>,    // PermissionType 이름 집합
)

/** 30 운영진 추가 후보 — 아직 운영진이 아닌 일반 회원. */
data class AdminCandidateResponse(
    val memberId: Long,               // club_member id (addAdmin 입력)
    val name: String,
    val initials: String,
    val cohortLabel: String,
)

// ── 요청 ──

data class AddAdminRequest(
    @field:Min(1) val memberId: Long,
    @field:NotBlank(message = "직함은 필수입니다.")
    @field:Size(max = 30, message = "직함은 30자 이하여야 합니다.")
    val title: String,
)

data class ChangeTitleRequest(
    @field:NotBlank(message = "직함은 필수입니다.")
    @field:Size(max = 30, message = "직함은 30자 이하여야 합니다.")
    val title: String,
)

data class TogglePermissionRequest(
    @field:NotBlank(message = "권한 종류는 필수입니다.")
    val type: String,                 // PermissionType (NOTICE_WRITE 등 6종)
)
