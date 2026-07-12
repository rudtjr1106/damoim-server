package com.damoim.server.settings

import com.damoim.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 알림 설정(65) — 유저 전역. 대상은 항상 JWT 주체 본인(IDOR 불가). */
@RestController
@RequestMapping("/api/me/notification-settings")
class NotificationSettingsController(private val service: NotificationSettingsService) {

    @GetMapping
    fun get(@AuthenticationPrincipal principal: UserPrincipal): NotifSettingsResponse =
        service.get(principal.userId)

    @PutMapping
    fun update(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody req: UpdateNotifSettingsRequest,
    ): NotifSettingsResponse = service.update(principal.userId, req)
}
