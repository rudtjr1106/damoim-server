package com.damoim.server.report

import com.damoim.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** 신고(82) 접수 + 조회. 경로가 /api/reports·/api/me/reports·/api/clubs/me/reports로 갈려 클래스 매핑 없이 둔다. */
@RestController
class ReportController(private val reportService: ReportService) {

    /** 82 게시글/댓글 신고 접수. */
    @PostMapping("/api/reports")
    fun submit(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody req: SubmitReportRequest,
    ) = reportService.submit(principal.userId, req)

    /** 34 내가 신고한 내역. */
    @GetMapping("/api/me/reports")
    fun mine(@AuthenticationPrincipal principal: UserPrincipal): List<MyReportResponse> =
        reportService.listMine(principal.userId)

    /** 35 운영진 — 동아리 신고 목록. */
    @GetMapping("/api/clubs/me/reports")
    fun clubReports(@AuthenticationPrincipal principal: UserPrincipal): List<ClubReportResponse> =
        reportService.listClubReports(principal.userId)
}
