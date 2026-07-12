package com.damoim.server.member

import jakarta.validation.constraints.NotBlank

/**
 * 명부 회원(16/17/18/20). 파생 필드는 서버가 계산해 내려준다:
 * - initials = 이름 끝 2글자(3글자 이상일 때), joinedLabel = "2026.06.18"
 * - isMe = 요청자 본인 여부(JWT 주체 기준). cohortId는 미배정 시 0.
 */
data class MemberResponse(
    val id: Long,               // club_member id
    val name: String,
    val initials: String,
    val cohortId: Long,         // 미배정이면 0(클라가 기수 목록에서 해석)
    val role: String,           // LEADER / STAFF / MEMBER
    val status: String,         // ACTIVE / DORMANT
    val email: String,
    val joinedLabel: String,    // "2026.06.18"
    val isMe: Boolean,
)

/** 18 회원 상세 — 명부 회원 + 활동 요약(실집계). */
data class MemberDetailResponse(
    val member: MemberResponse,
    val cohortLabel: String,        // "2024학년 1기 (24기)" — cohortId에서 해석, 미배정이면 ""
    val postCount: Int,             // 작성 글 수(삭제 제외)
    val eventCount: Int,            // 이벤트 참여 수(APPLIED)
    val lastActiveLabel: String,    // "1시간 전" / "활동 없음"
)

/** 42 기수 변경. */
data class ChangeCohortRequest(val cohortId: Long)

/** 18 역할 변경 — STAFF ↔ MEMBER 만 허용(LEADER 위임은 별도, 서버에서 거부). */
data class ChangeRoleRequest(
    @field:NotBlank(message = "역할은 필수입니다.")
    val role: String,
)
