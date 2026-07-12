package com.damoim.server.board

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

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
    val comments: List<CommentResponse>,
)

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
