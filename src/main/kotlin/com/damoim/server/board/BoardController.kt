package com.damoim.server.board

import com.damoim.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/board")
class BoardController(private val boardService: BoardService) {

    /** 게시판 홈(10) — 필독 + 피드. */
    @GetMapping("/home")
    fun home(@AuthenticationPrincipal principal: UserPrincipal): BoardHomeResponse =
        boardService.home(principal.userId)

    /** 목록(11/12/13) — category 선택(NOTICE/FREE/RECRUIT, 없으면 전체). */
    @GetMapping("/posts")
    fun list(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) category: String?,
    ): List<PostSummaryResponse> = boardService.list(principal.userId, category)

    /** 상세(14/36). */
    @GetMapping("/posts/{id}")
    fun detail(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): PostDetailResponse = boardService.detail(principal.userId, id)

    /** 작성(15). */
    @PostMapping("/posts")
    fun create(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody req: CreatePostRequest,
    ): PostDetailResponse = boardService.create(principal.userId, req)

    /** 수정 — 작성자 본인만. */
    @PatchMapping("/posts/{id}")
    fun update(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
        @Valid @RequestBody req: UpdatePostRequest,
    ): PostDetailResponse = boardService.update(principal.userId, id, req)

    /** 삭제 — 작성자 또는 운영진. */
    @DeleteMapping("/posts/{id}")
    fun delete(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ) = boardService.delete(principal.userId, id)

    /** 필독 토글 — 운영진. */
    @PostMapping("/posts/{id}/pin")
    fun togglePin(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): Map<String, Boolean> = boardService.togglePin(principal.userId, id)
}
