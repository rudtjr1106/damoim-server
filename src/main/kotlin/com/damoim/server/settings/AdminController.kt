package com.damoim.server.settings

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
import org.springframework.web.bind.annotation.RestController

/** 운영진 권한(30/64) — 전부 동아리장 전용. */
@RestController
@RequestMapping("/api/admins")
class AdminController(private val adminService: AdminService) {

    /** 30 운영진 목록. */
    @GetMapping
    fun list(@AuthenticationPrincipal principal: UserPrincipal): List<AdminMemberResponse> =
        adminService.list(principal.userId)

    /** 30 운영진 지정 후보(일반 회원). */
    @GetMapping("/assignable")
    fun assignable(@AuthenticationPrincipal principal: UserPrincipal): List<AdminCandidateResponse> =
        adminService.assignable(principal.userId)

    /** 30 운영진 지정. */
    @PostMapping
    fun addAdmin(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody req: AddAdminRequest,
    ) = adminService.addAdmin(principal.userId, req)

    /** 30 권한 토글. */
    @PostMapping("/{userId}/permissions/toggle")
    fun togglePermission(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable userId: Long,
        @Valid @RequestBody req: TogglePermissionRequest,
    ) = adminService.togglePermission(principal.userId, userId, req.type)

    /** 64 직함 변경. */
    @PatchMapping("/{userId}/title")
    fun changeTitle(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable userId: Long,
        @Valid @RequestBody req: ChangeTitleRequest,
    ) = adminService.changeTitle(principal.userId, userId, req.title)

    /** 64 운영진 해제. */
    @DeleteMapping("/{userId}")
    fun removeAdmin(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable userId: Long,
    ) = adminService.removeAdmin(principal.userId, userId)
}
