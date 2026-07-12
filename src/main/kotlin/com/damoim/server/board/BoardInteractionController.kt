package com.damoim.server.board

import com.damoim.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/board")
class BoardInteractionController(private val interactions: BoardInteractionService) {

    /** 좋아요 토글. */
    @PostMapping("/posts/{id}/like")
    fun like(@AuthenticationPrincipal p: UserPrincipal, @PathVariable id: Long): LikeResponse =
        interactions.toggleLike(p.userId, id)

    /** 투표(단일=교체, 복수=토글). */
    @PostMapping("/posts/{id}/poll/vote")
    fun vote(
        @AuthenticationPrincipal p: UserPrincipal,
        @PathVariable id: Long,
        @Valid @RequestBody req: VotePollRequest,
    ): PollResponse = interactions.votePoll(p.userId, id, req.optionIndex)

    /** 투표 회수(다시 투표). */
    @DeleteMapping("/posts/{id}/poll/vote")
    fun clearVote(@AuthenticationPrincipal p: UserPrincipal, @PathVariable id: Long): PollResponse =
        interactions.clearPollVote(p.userId, id)

    /** 모집 신청(정원 도달 시 자동 마감). */
    @PostMapping("/posts/{id}/recruit/apply")
    fun applyRecruit(@AuthenticationPrincipal p: UserPrincipal, @PathVariable id: Long): RecruitResponse =
        interactions.applyRecruit(p.userId, id)

    /** 댓글 작성(parentId 있으면 답글). */
    @PostMapping("/posts/{id}/comments")
    fun addComment(
        @AuthenticationPrincipal p: UserPrincipal,
        @PathVariable id: Long,
        @Valid @RequestBody req: AddCommentRequest,
    ): CommentResponse = interactions.addComment(p.userId, id, req)

    /** 댓글 삭제(작성자 또는 운영진). */
    @DeleteMapping("/comments/{commentId}")
    fun deleteComment(@AuthenticationPrincipal p: UserPrincipal, @PathVariable commentId: Long) =
        interactions.deleteComment(p.userId, commentId)
}
