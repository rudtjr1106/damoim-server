package com.damoim.server.schedule

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

/**
 * 일정/이벤트(F). 대상 동아리는 JWT 주체의 활성 동아리에서만 해석 — clubId를 받지 않아 IDOR 차단.
 * 관리(등록/수정/삭제/조기마감/공지)는 동아리장, 조회·신청·내일정은 활성 회원.
 */
@RestController
@RequestMapping("/api/schedules")
class ScheduleController(
    private val scheduleService: ScheduleService,
    private val applicationService: EventApplicationService,
) {
    // ── 조회 ──

    @GetMapping
    fun list(@AuthenticationPrincipal principal: UserPrincipal): List<ScheduleResponse> =
        scheduleService.list(principal.userId)

    /** 48 내 신청 내역 — /{id}보다 먼저 매칭되는 리터럴 경로. */
    @GetMapping("/my-applications")
    fun myApplications(@AuthenticationPrincipal principal: UserPrincipal): List<MyApplicationResponse> =
        applicationService.myApplications(principal.userId)

    @GetMapping("/{id}")
    fun detail(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): ScheduleResponse = scheduleService.detail(principal.userId, id)

    // ── CRUD (LEADER) ──

    @PostMapping
    fun create(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody req: SaveScheduleRequest,
    ): Long = scheduleService.create(principal.userId, req)

    @PatchMapping("/{id}")
    fun update(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
        @Valid @RequestBody req: SaveScheduleRequest,
    ): Long = scheduleService.update(principal.userId, id, req)

    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ) = scheduleService.delete(principal.userId, id)

    // ── 운영 액션 (LEADER) ──

    @PostMapping("/{id}/close")
    fun closeEarly(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ) = scheduleService.closeEarly(principal.userId, id)

    @PostMapping("/{id}/announce")
    fun announce(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ) = scheduleService.announce(principal.userId, id)

    // ── 참여/내일정 (활성 회원) ──

    @PostMapping("/{id}/calendar/toggle")
    fun toggleMyCalendar(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): Boolean = scheduleService.toggleMyCalendar(principal.userId, id)

    @PostMapping("/{id}/apply")
    fun apply(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
        @Valid @RequestBody req: ApplicationAnswersRequest,
    ) = applicationService.apply(principal.userId, id, req.answers.map { QuestionAnswerDto(it.question, it.answer) })

    @PatchMapping("/{id}/my-application")
    fun updateAnswers(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
        @Valid @RequestBody req: ApplicationAnswersRequest,
    ) = applicationService.updateAnswers(principal.userId, id, req.answers.map { QuestionAnswerDto(it.question, it.answer) })

    @DeleteMapping("/{id}/my-application")
    fun cancel(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ) = applicationService.cancel(principal.userId, id)
}
