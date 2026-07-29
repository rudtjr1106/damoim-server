package com.damoim.server.settings

import com.damoim.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 차단 관리(83) — 동아리장 전용. */
@RestController
@RequestMapping("/api/blocked")
class BlockedController(private val blockedService: BlockedService) {

    @GetMapping
    fun list(@AuthenticationPrincipal principal: UserPrincipal): List<BlockedUserResponse> =
        blockedService.list(principal.userId)

    /** 83 차단 등록 — 이미 차단된 회원이면 기존 항목을 그대로 반환(멱등). */
    @PostMapping
    fun block(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody req: BlockUserRequest,
    ): BlockedUserResponse = blockedService.block(principal.userId, req)

    @DeleteMapping("/{id}")
    fun unblock(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ) = blockedService.unblock(principal.userId, id)
}
