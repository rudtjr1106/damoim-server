package com.damoim.server.report

import com.damoim.server.domain.enums.ReportReason
import jakarta.validation.constraints.NotNull

/** 신고 대상 유형. post_reports는 게시글/댓글만 지원한다. */
enum class ReportTargetType { POST, COMMENT }

/** 82 신고 접수 요청. */
data class SubmitReportRequest(
    @field:NotNull val targetType: ReportTargetType? = null,
    @field:NotNull val targetId: Long? = null,
    @field:NotNull val reason: ReportReason? = null,
    val detail: String? = null,
)

/** 34 내가 신고한 내역. reason 라벨은 클라가 매핑한다. */
data class MyReportResponse(
    val id: Long,
    val targetType: ReportTargetType,
    val targetPreview: String,
    val reason: ReportReason,
    val reportedUserName: String,
    val reportedUserImageUrl: String?,
    val createdLabel: String,
)

/** 35 운영진용 동아리 신고 목록 — 신고자/피신고자를 함께 노출. */
data class ClubReportResponse(
    val id: Long,
    val targetType: ReportTargetType,
    val targetPreview: String,
    val reason: ReportReason,
    val reporterName: String,
    val reportedUserName: String,
    val createdLabel: String,
)
