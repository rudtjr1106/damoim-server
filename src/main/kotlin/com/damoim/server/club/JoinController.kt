package com.damoim.server.club

import com.damoim.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/clubs")
class JoinController(private val joinService: JoinService) {

    /** 가입 코드 제출(03) → 대기 신청. */
    @PostMapping("/join")
    fun submitCode(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody req: JoinCodeRequest,
    ): JoinResultResponse = joinService.submitCode(principal.userId, req.code)

    /** 가입 신청 관리(09) — LEADER. */
    @GetMapping("/me/applicants")
    fun applicants(@AuthenticationPrincipal principal: UserPrincipal): ApplicantsBoardResponse =
        joinService.applicants(principal.userId)

    /** 승인/거절(09) — LEADER. */
    @PostMapping("/me/applicants/{applicationId}/decide")
    fun decide(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable applicationId: Long,
        @Valid @RequestBody req: DecideRequest,
    ) = joinService.decide(principal.userId, applicationId, req)
}
