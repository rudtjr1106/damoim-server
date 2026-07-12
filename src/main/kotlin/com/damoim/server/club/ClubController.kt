package com.damoim.server.club

import com.damoim.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
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

    /** 기수 추가(44) — LEADER. */
    @PostMapping("/me/cohorts")
    fun addCohort(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody req: CohortCreateRequest,
    ): CohortResponse = clubService.addCohort(principal.userId, req)

    /** 기수 이름 변경(19) — LEADER. */
    @PatchMapping("/me/cohorts/{cohortId}")
    fun renameCohort(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable cohortId: Long,
        @Valid @RequestBody req: CohortRenameRequest,
    ) = clubService.renameCohort(principal.userId, cohortId, req)

    /** 내가 속한 동아리 목록(33 전환 시트). */
    @GetMapping("/joined")
    fun joinedClubs(@AuthenticationPrincipal principal: UserPrincipal): List<ClubMembershipResponse> =
        clubService.joinedClubs(principal.userId)

    /** 활성 동아리 전환(33). */
    @PostMapping("/switch")
    fun switchClub(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody req: SwitchClubRequest,
    ): ClubResponse = clubService.switchClub(principal.userId, req.clubId)

    /** 동아리 탈퇴(60). */
    @PostMapping("/me/leave")
    fun leaveClub(@AuthenticationPrincipal principal: UserPrincipal) =
        clubService.leaveClub(principal.userId)

    /** 가입 코드 재발급(08·59) — LEADER. */
    @PostMapping("/me/join-code/regenerate")
    fun regenerateJoinCode(@AuthenticationPrincipal principal: UserPrincipal): JoinCodeResponse =
        clubService.regenerateJoinCode(principal.userId)

    /** 가입 코드 비활성화(08) — LEADER. */
    @PostMapping("/me/join-code/disable")
    fun disableJoinCode(@AuthenticationPrincipal principal: UserPrincipal): JoinCodeResponse =
        clubService.disableJoinCode(principal.userId)
}
