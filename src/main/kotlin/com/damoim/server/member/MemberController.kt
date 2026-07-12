package com.damoim.server.member

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

/**
 * 회원 명부·상세·관리(E). 대상 동아리는 JWT 주체의 활성 동아리에서만 해석 —
 * path/body로 clubId를 받지 않아 IDOR을 원천 차단한다.
 */
@RestController
@RequestMapping("/api/members")
class MemberController(private val memberService: MemberService) {

    /** 명부(16 허브·17 목록). 허브 통계·필터·검색은 클라가 이 목록에서 파생. */
    @GetMapping
    fun list(@AuthenticationPrincipal principal: UserPrincipal): List<MemberResponse> =
        memberService.list(principal.userId)

    /** 내 명부 정보(20). */
    @GetMapping("/me")
    fun myMember(@AuthenticationPrincipal principal: UserPrincipal): MemberResponse =
        memberService.myMember(principal.userId)

    /** 회원 상세(18). */
    @GetMapping("/{id}")
    fun detail(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): MemberDetailResponse = memberService.detail(principal.userId, id)

    /** 기수 변경(42) — LEADER. */
    @PostMapping("/{id}/cohort")
    fun changeCohort(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
        @Valid @RequestBody req: ChangeCohortRequest,
    ) = memberService.changeCohort(principal.userId, id, req.cohortId)

    /** 역할 변경(18) — LEADER. */
    @PostMapping("/{id}/role")
    fun changeRole(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
        @Valid @RequestBody req: ChangeRoleRequest,
    ) = memberService.changeRole(principal.userId, id, req.role)

    /** 내보내기(43) — LEADER. */
    @DeleteMapping("/{id}")
    fun remove(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ) = memberService.remove(principal.userId, id)

    /** 동아리장 위임 — LEADER. 대상을 LEADER로, 본인은 STAFF로. (리더가 탈퇴하려면 먼저 위임) */
    @PostMapping("/{id}/transfer-leadership")
    fun transferLeadership(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ) = memberService.transferLeadership(principal.userId, id)
}
