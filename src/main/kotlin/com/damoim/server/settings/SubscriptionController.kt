package com.damoim.server.settings

import com.damoim.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 구독(27/29/49/50). 상태·결제·해지는 동아리장, 플랜 카탈로그는 인증만. */
@RestController
@RequestMapping("/api/subscription")
class SubscriptionController(private val subscriptionService: SubscriptionService) {

    /** 29 구독 관리 상태 — LEADER. */
    @GetMapping
    fun state(@AuthenticationPrincipal principal: UserPrincipal): SubscriptionStateResponse =
        subscriptionService.state(principal.userId)

    /** 27 플랜 카탈로그. */
    @GetMapping("/plans")
    fun plans(): List<SubscriptionPlanResponse> = subscriptionService.plans()

    /** 인앱결제 성공 후 활성화(27→49) — LEADER. */
    @PostMapping("/subscribe")
    fun subscribe(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody req: SubscribeRequest,
    ): SubscriptionStateResponse = subscriptionService.subscribe(principal.userId, req)

    /** 구독 해지 → 무료 전환(29) — LEADER. */
    @PostMapping("/cancel")
    fun cancel(@AuthenticationPrincipal principal: UserPrincipal): SubscriptionStateResponse =
        subscriptionService.cancel(principal.userId)
}
