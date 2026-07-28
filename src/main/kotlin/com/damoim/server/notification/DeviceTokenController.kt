package com.damoim.server.notification

import com.damoim.server.security.UserPrincipal
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * FCM 디바이스 토큰 등록/해제 — 대상은 항상 JWT 주체 본인(IDOR 불가). deny-by-default라
 * SecurityConfig 수정 없이 인증 보호됨. 응답은 ApiResponseWrapper가 봉투로 자동 래핑.
 */
@RestController
@RequestMapping("/api/me/device-token")
class DeviceTokenController(private val service: DeviceTokenService) {

    @PostMapping
    fun register(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody req: RegisterDeviceTokenRequest,
    ) {
        service.register(principal.userId, req.token, req.platform)
    }

    /** 로그아웃 시 해당 기기 토큰 해제(다른 계정에 알림 새는 것 방지). */
    @DeleteMapping
    fun unregister(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam token: String,
    ) {
        service.unregister(token)
    }
}

data class RegisterDeviceTokenRequest(
    @field:NotBlank val token: String = "",
    val platform: String? = null,   // ANDROID | IOS
)
