package com.damoim.server.notification

import com.damoim.server.security.UserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/me/notifications")
class NotificationController(private val notificationService: NotificationService) {

    /** 내 알림 목록(37). */
    @GetMapping
    fun list(@AuthenticationPrincipal principal: UserPrincipal): List<NotificationResponse> =
        notificationService.list(principal.userId)

    /** 모두 읽음. */
    @PostMapping("/read-all")
    fun markAllRead(@AuthenticationPrincipal principal: UserPrincipal) =
        notificationService.markAllRead(principal.userId)
}
