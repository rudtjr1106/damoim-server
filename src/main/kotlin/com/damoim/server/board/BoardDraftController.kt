package com.damoim.server.board

import com.damoim.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/board/draft")
class BoardDraftController(private val draftService: BoardDraftService) {

    /** 임시저장 불러오기 — 없으면 data:null. */
    @GetMapping
    fun load(@AuthenticationPrincipal p: UserPrincipal): DraftResponse? = draftService.load(p.userId)

    /** 임시저장(유저당 1건, upsert). */
    @PutMapping
    fun save(@AuthenticationPrincipal p: UserPrincipal, @Valid @RequestBody req: DraftRequest): DraftResponse =
        draftService.save(p.userId, req)

    /** 임시저장 삭제. */
    @DeleteMapping
    fun clear(@AuthenticationPrincipal p: UserPrincipal) = draftService.clear(p.userId)
}
