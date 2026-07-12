package com.damoim.server.club

import com.damoim.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/clubs")
class ClubController(private val clubService: ClubService) {

    /** 동아리 생성(07). */
    @PostMapping
    fun create(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody req: CreateClubRequest,
    ): ClubResponse = clubService.create(principal.userId, req)

    /** 내 활성 동아리(08 정보). */
    @GetMapping("/me")
    fun myClub(@AuthenticationPrincipal principal: UserPrincipal): ClubResponse =
        clubService.myClub(principal.userId)

    /** 홈 요약(05/06). */
    @GetMapping("/me/home")
    fun home(@AuthenticationPrincipal principal: UserPrincipal): HomeSummaryResponse =
        clubService.home(principal.userId)

    /** 기수 목록(19 등에서 사용). */
    @GetMapping("/me/cohorts")
    fun cohorts(@AuthenticationPrincipal principal: UserPrincipal): List<CohortResponse> =
        clubService.cohorts(principal.userId)

    /** 가입 코드 재발급(08·59) — LEADER. */
    @PostMapping("/me/join-code/regenerate")
    fun regenerateJoinCode(@AuthenticationPrincipal principal: UserPrincipal): JoinCodeResponse =
        clubService.regenerateJoinCode(principal.userId)

    /** 가입 코드 비활성화(08) — LEADER. */
    @PostMapping("/me/join-code/disable")
    fun disableJoinCode(@AuthenticationPrincipal principal: UserPrincipal): JoinCodeResponse =
        clubService.disableJoinCode(principal.userId)
}
