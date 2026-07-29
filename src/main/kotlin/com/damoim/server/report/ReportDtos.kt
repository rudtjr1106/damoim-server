package com.damoim.server.report

import com.damoim.server.domain.enums.ReportAction
import com.damoim.server.domain.enums.ReportReason
import com.damoim.server.domain.enums.ReportStatus
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

/**
 * 35 운영진 신고 처리 요청. decision은 RESOLVED(처리완료)/REJECTED(기각)만 허용하고,
 * action은 처리완료일 때만 콘텐츠 삭제를 지정할 수 있다(기각인데 삭제하는 모순 방지).
 */
data class HandleReportRequest(
    @field:NotNull val decision: ReportStatus? = null,
    val action: ReportAction = ReportAction.NONE,
)

/** 34 내가 신고한 내역. reason 라벨은 클라가 매핑한다. */
data class MyReportResponse(
    val id: Long,
    val targetType: ReportTargetType,
    val targetPreview: String,
    val reason: ReportReason,
    val status: ReportStatus,          // 처리 여부 노출(UGC 정책상 신고자에게 결과가 보여야 함)
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
    val status: ReportStatus,          // 대기 건만 처리 버튼을 띄우도록 클라가 분기
    val action: ReportAction?,         // 처리 시 취한 조치(미처리면 null)
    val reporterName: String,
    val reportedUserName: String,
    val createdLabel: String,
)
