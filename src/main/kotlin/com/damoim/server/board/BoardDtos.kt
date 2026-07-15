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

/**
 * 첨부 입력(다형 — type별 필드).
 * IMAGE/FILE_DOC은 사전에 presigned PUT으로 S3에 올린 [storageKey]를 전달(서버가 소유권·실크기 검증).
 * LINK는 [linkUrl] 전체 URL(웹 이동용)을 전달.
 */
data class AttachmentInput(
    @field:Pattern(regexp = "IMAGE|FILE_DOC|LINK", message = "첨부 유형이 올바르지 않습니다.")
    val type: String,
    @field:Size(max = 1024) val storageKey: String? = null,     // IMAGE/FILE_DOC — S3 오브젝트 키
    @field:Size(max = 200) val imageLabel: String? = null,      // IMAGE 캡션(선택)
    @field:Size(max = 255) val fileName: String? = null,        // FILE_DOC 파일명
    @field:Min(value = 1, message = "파일 크기가 올바르지 않습니다.")
    val fileSizeBytes: Long? = null,                            // FILE_DOC 클라 선언 크기(S3 실측으로 교정)
    @field:Size(max = 300) val linkTitle: String? = null,       // LINK
    @field:Size(max = 255) val linkDomain: String? = null,      // LINK
    @field:Size(max = 2048) val linkUrl: String? = null,        // LINK 전체 URL
)

/** 게시판 첨부 업로드 URL 요청(1단계). kind=IMAGE|FILE_DOC. */
data class BoardUploadUrlRequest(
    @field:NotBlank(message = "파일명은 필수입니다.")
    @field:Size(max = 255)
    val fileName: String,
    @field:Size(max = 255) val contentType: String? = null,
    @field:Min(value = 1, message = "파일 크기가 올바르지 않습니다.")
    val sizeBytes: Long,
    @field:Pattern(regexp = "IMAGE|FILE_DOC", message = "업로드 유형이 올바르지 않습니다.")
    val kind: String,
)

/** 업로드 URL 응답 — 클라가 이 URL로 S3에 직접 PUT 후, storageKey를 첨부로 등록. */
data class BoardUploadUrlResponse(
    val uploadUrl: String,
    val storageKey: String,
    val expiresInSeconds: Long,
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
    // 수정 시 첨부 전체 집합(기존 이미지/문서는 storageKey로 재참조, 새 것만 실업로드 키). 서버가 전체 교체.
    @field:Valid
    @field:Size(max = 10, message = "첨부는 최대 10개입니다.")
    val attachments: List<AttachmentInput> = emptyList(),
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
    val thumbnailUrl: String?,          // 첫 이미지 첨부의 presigned view URL(없으면 null)
    val readRate: Int?,
    val recruit: RecruitResponse? = null,   // RECRUIT 카테고리 카드의 진행률·마감(그 외 null)
    val authorImageUrl: String? = null,     // 작성자 프로필 사진(없으면 null → 이니셜)
)

/** 상세(14/36). 첨부·투표·모집은 C1b, 좋아요/조회수 실집계는 C2에서 채운다. */
data class PostDetailResponse(
    val id: Long,
    val category: String,
    val title: String,
    val content: String,
    val authorName: String,
    val authorInitials: String,
    val authorImageUrl: String?,        // 작성자 프로필 사진(없으면 null → 이니셜)
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

/** 첨부 응답(다형 flat). imageUrl/fileUrl은 읽을 때마다 발급되는 presigned URL. */
data class AttachmentResponse(
    val type: String,
    val imageUrl: String?,      // IMAGE — presigned view URL(인라인 렌더)
    val imageLabel: String?,    // IMAGE 캡션(선택)
    val fileName: String?,
    val fileSize: String?,      // sizeBytes 파생 라벨
    val fileUrl: String?,       // FILE_DOC — presigned download URL
    val linkTitle: String?,
    val linkDomain: String?,
    val linkUrl: String?,       // LINK 전체 URL(웹 이동)
    val storageKey: String?,    // IMAGE/FILE_DOC — 수정 시 기존 첨부 재참조용
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
    val applicants: List<RecruitApplicantResponse> = emptyList(),   // 상세 신청자 아바타 스택(목록/홈은 빈 배열)
)

/** 모집 신청자(84 아바타 스택/명단). */
data class RecruitApplicantResponse(
    val name: String,
    val initials: String,
    val imageUrl: String?,
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
    val authorImageUrl: String?,   // 작성자 프로필 사진(없으면 null → 이니셜)
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

// ── 검색(40/85) ──
data class SearchResultResponse(val query: String, val posts: List<PostSummaryResponse>)

/** 85 검색 시작 — 최근·추천 검색어. */
data class SearchSuggestionsResponse(val recent: List<String>, val recommended: List<String>)

// ── 임시저장(작성 초안, 유저 1건) ──
/** 초안 저장 요청. 작성 중이라 모든 필드 lax(불완전 허용). */
data class DraftRequest(
    val category: String? = null,
    @field:Size(max = 200) val title: String? = null,
    @field:Size(max = 20000) val content: String? = null,
    val pinned: Boolean = false,
    @field:Valid @field:Size(max = 10) val attachments: List<AttachmentInput> = emptyList(),
    @field:Valid val poll: PollInput? = null,
    @field:Valid val recruit: RecruitInput? = null,
)

data class DraftResponse(
    val category: String,
    val title: String,
    val content: String,
    val pinned: Boolean,
    val attachments: List<AttachmentInput>,
    val poll: PollInput?,
    val recruit: RecruitInput?,
)

/** 초안의 리치 콘텐츠(payload jsonb 직렬화 대상). */
data class DraftPayload(
    val attachments: List<AttachmentInput> = emptyList(),
    val poll: PollInput? = null,
    val recruit: RecruitInput? = null,
)
