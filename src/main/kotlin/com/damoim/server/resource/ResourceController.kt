package com.damoim.server.resource

import com.damoim.server.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/resources")
class ResourceController(private val resourceService: ResourceService) {

    /** 자료 목록(67) — folder 선택, 기수공개 필터 적용. */
    @GetMapping
    fun list(
        @AuthenticationPrincipal p: UserPrincipal,
        @RequestParam(required = false) folder: String?,
    ): List<ResourceResponse> = resourceService.list(p.userId, folder)

    /** 저장공간(67 바). */
    @GetMapping("/storage")
    fun storage(@AuthenticationPrincipal p: UserPrincipal): StorageUsageResponse =
        resourceService.storage(p.userId)

    /** 자료 상세(68). */
    @GetMapping("/{id}")
    fun detail(@AuthenticationPrincipal p: UserPrincipal, @PathVariable id: Long): ResourceResponse =
        resourceService.detail(p.userId, id)

    /** 1단계 — 업로드 URL 발급(69). */
    @PostMapping("/upload-url")
    fun uploadUrl(
        @AuthenticationPrincipal p: UserPrincipal,
        @Valid @RequestBody req: UploadUrlRequest,
    ): UploadUrlResponse = resourceService.createUploadUrl(p.userId, req)

    /** 2단계 — 업로드 완료 후 등록(69). */
    @PostMapping
    fun create(
        @AuthenticationPrincipal p: UserPrincipal,
        @Valid @RequestBody req: CreateResourceRequest,
    ): ResourceResponse = resourceService.create(p.userId, req)

    /** 자료 삭제(업로더 또는 운영진). */
    @DeleteMapping("/{id}")
    fun delete(@AuthenticationPrincipal p: UserPrincipal, @PathVariable id: Long) =
        resourceService.delete(p.userId, id)

    /** 다운로드 URL 발급 + 카운트 증가(68). */
    @GetMapping("/{id}/download-url")
    fun downloadUrl(@AuthenticationPrincipal p: UserPrincipal, @PathVariable id: Long): DownloadUrlResponse =
        resourceService.createDownloadUrl(p.userId, id)
}
