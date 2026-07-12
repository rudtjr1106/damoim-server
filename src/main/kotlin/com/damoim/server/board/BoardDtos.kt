package com.damoim.server.board

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

// ── 요청 ──
data class CreatePostRequest(
    @field:Pattern(regexp = "NOTICE|FREE|RECRUIT", message = "카테고리가 올바르지 않습니다.")
    val category: String,
    @field:NotBlank(message = "제목은 필수입니다.")
    @field:Size(max = 200, message = "제목은 200자 이하여야 합니다.")
    val title: String,
    @field:NotBlank(message = "내용은 필수입니다.")
    @field:Size(max = 20000, message = "내용이 너무 깁니다.")
    val content: String,
    val pinned: Boolean = false,
    @field:Valid
    @field:Size(max = 10, message = "첨부는 최대 10개입니다.")
    val attachments: List<AttachmentInput> = emptyList(),
    @field:Valid val poll: PollInput? = null,
    @field:Valid val recruit: RecruitInput? = null,
)

/** 첨부 입력(다형 — type별 필드). 파일 크기는 클라가 바이트로 전달. */
data class AttachmentInput(
    @field:Pattern(regexp = "IMAGE|FILE_DOC|LINK", message = "첨부 유형이 올바르지 않습니다.")
    val type: String,
    @field:Size(max = 200) val imageLabel: String? = null,
    @field:Size(max = 255) val fileName: String? = null,
    @field:Min(value = 1, message = "파일 크기가 올바르지 않습니다.")
    val fileSizeBytes: Long? = null,
    @field:Size(max = 300) val linkTitle: String? = null,
    @field:Size(max = 255) val linkDomain: String? = null,
)

/** 투표 입력(옵션 2~10개). deadline은 ISO-8601, 라벨/D-day는 서버가 파생. */
data class PollInput(
    @field:Size(min = 2, max = 10, message = "투표 항목은 2~10개여야 합니다.")
    val options: List<String>,
    val anonymous: Boolean = false,
    val multiSelect: Boolean = false,
    val deadline: Instant? = null,
)

/** 모집 입력. deadline은 ISO-8601, 라벨/D-day는 서버가 파생. */
data class RecruitInput(
    @field:Min(value = 1, message = "정원은 1명 이상이어야 합니다.")
    val capacity: Int,
    val deadline: Instant? = null,
    @field:Size(max = 32) val method: String? = null,
)

data class UpdatePostRequest(
    @field:Pattern(regexp = "NOTICE|FREE|RECRUIT", message = "카테고리가 올바르지 않습니다.")
    val category: String,
    @field:NotBlank(message = "제목은 필수입니다.")
    @field:Size(max = 200)
    val title: String,
    @field:NotBlank(message = "내용은 필수입니다.")
    @field:Size(max = 20000)
    val content: String,
)

// ── 응답 ──
/** 목록 카드(11/12/13). */
data class PostSummaryResponse(
    val id: Long,
    val category: String,
    val title: String,
    val preview: String,
    val authorName: String,
    val authorInitials: String,
    val authorGisu: String?,
    val timeLabel: String,
    val likeCount: Int,
    val likedByMe: Boolean,
    val commentCount: Int,
    val isPinned: Boolean,
    val isAuthorLeader: Boolean,
    val hasThumbnail: Boolean,
    val readRate: Int?,
)

/** 상세(14/36). 첨부·투표·모집은 C1b, 좋아요/조회수 실집계는 C2에서 채운다. */
data class PostDetailResponse(
    val id: Long,
    val category: String,
    val title: String,
    val content: String,
    val authorName: String,
    val authorInitials: String,
    val authorGisu: String?,
    val timeLabel: String,
    val dateLabel: String,
    val viewCount: Int,
    val likeCount: Int,
    val likedByMe: Boolean,
    val commentCount: Int,
    val isPinned: Boolean,
    val isAuthorLeader: Boolean,
    val isMine: Boolean,
    val readRate: Int?,
    val attachments: List<AttachmentResponse>,
    val poll: PollResponse?,
    val recruit: RecruitResponse?,
    val comments: List<CommentResponse>,
)

/** 첨부 응답(다형 flat). fileSize는 sizeBytes 파생 라벨. */
data class AttachmentResponse(
    val type: String,
    val imageLabel: String?,
    val fileName: String?,
    val fileSize: String?,
    val linkTitle: String?,
    val linkDomain: String?,
)

/** 투표 표시. C1b에선 votes/myVotes는 0/빈값(실투표는 C2). */
data class PollResponse(
    val anonymous: Boolean,
    val multiSelect: Boolean,
    val deadlineLabel: String?,
    val dday: String?,
    val totalVotes: Int,
    val myVotes: List<Int>,
    val options: List<PollOptionResponse>,
)

data class PollOptionResponse(val index: Int, val label: String, val votes: Int, val percent: Int)

/** 모집 표시. C1b에선 current/appliedByMe는 0/false(실신청은 C2). */
data class RecruitResponse(
    val status: String,
    val capacity: Int,
    val current: Int,
    val remaining: Int,
    val percent: Int,
    val deadlineLabel: String?,
    val dday: String?,
    val method: String?,
    val appliedByMe: Boolean,
)

// ── 상호작용 요청/응답 ──
data class VotePollRequest(
    @field:Min(value = 0, message = "옵션 인덱스가 올바르지 않습니다.")
    val optionIndex: Int,
)

data class AddCommentRequest(
    @field:NotBlank(message = "댓글 내용은 필수입니다.")
    @field:Size(max = 1000, message = "댓글은 1000자 이하여야 합니다.")
    val content: String,
    val parentId: Long? = null,
)

data class LikeResponse(val liked: Boolean, val likeCount: Int)

data class CommentResponse(
    val id: Long,
    val authorName: String,
    val authorInitials: String,
    val timeLabel: String,
    val content: String,
    val isReply: Boolean,
    val isAuthor: Boolean,     // 글 작성자의 댓글인지(작성자 뱃지)
    val parentId: Long?,
)

/** 홈(10). 필독 배너 + 최신 피드. */
data class BoardHomeResponse(
    val pinned: List<PostSummaryResponse>,
    val feed: List<PostSummaryResponse>,
)
