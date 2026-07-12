package com.damoim.server.board

import com.damoim.server.security.UserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/board/search")
class BoardSearchController(private val boardService: BoardService) {

    /** 검색 결과(40) — 제목/본문/작성자. 검색어는 최근 검색어로 기록됨. */
    @GetMapping
    fun search(
        @AuthenticationPrincipal p: UserPrincipal,
        @RequestParam(name = "q", defaultValue = "") q: String,
    ): SearchResultResponse = boardService.search(p.userId, q)

    /** 검색 시작(85) — 최근·추천 검색어. */
    @GetMapping("/suggestions")
    fun suggestions(@AuthenticationPrincipal p: UserPrincipal): SearchSuggestionsResponse =
        boardService.searchSuggestions(p.userId)

    /** 최근 검색어 삭제 — q 있으면 해당 항목, 없으면 전체 삭제. */
    @DeleteMapping("/recent")
    fun deleteRecent(
        @AuthenticationPrincipal p: UserPrincipal,
        @RequestParam(name = "q", required = false) q: String?,
    ) {
        if (q.isNullOrBlank()) boardService.clearRecentSearches(p.userId)
        else boardService.removeRecentSearch(p.userId, q)
    }
}
