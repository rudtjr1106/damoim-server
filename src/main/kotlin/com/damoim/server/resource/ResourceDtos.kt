package com.damoim.server.resource

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

private const val FOLDERS = "DOCS|ACCOUNTING|PRESENTATION|PHOTOS"

// ── 업로드 URL 발급 요청(1단계) ──
data class UploadUrlRequest(
    @field:NotBlank @field:Size(max = 255) val fileName: String,
    @field:Size(max = 100) val contentType: String? = null,
    @field:Min(1) val sizeBytes: Long,
    @field:Pattern(regexp = FOLDERS, message = "폴더가 올바르지 않습니다.") val folder: String,
)

data class UploadUrlResponse(val uploadUrl: String, val storageKey: String, val expiresInSeconds: Long)

// ── 자료 등록(2단계, S3 업로드 완료 후) ──
data class CreateResourceRequest(
    @field:NotBlank @field:Size(max = 200) val title: String,
    @field:Size(max = 2000) val description: String = "",
    @field:Pattern(regexp = FOLDERS, message = "폴더가 올바르지 않습니다.") val folder: String,
    @field:Pattern(regexp = "ALL_MEMBERS|COHORT_ONLY", message = "공개 범위가 올바르지 않습니다.")
    val visibility: String = "ALL_MEMBERS",
    val cohortIds: List<Long> = emptyList(),
    @field:NotBlank @field:Size(max = 255) val fileName: String,
    @field:Size(max = 16) val ext: String = "",
    @field:Min(1) val sizeBytes: Long,
    @field:NotBlank @field:Size(max = 500) val storageKey: String,
    val pageCount: Int? = null,
)

// ── 응답 ──
data class ResourceResponse(
    val id: Long,
    val title: String,
    val fileName: String,
    val ext: String,
    val description: String,
    val folder: String,
    val sizeLabel: String,
    val sizeBytes: Long,
    val uploaderName: String,
    val uploaderIsLeader: Boolean,
    val uploadedLabel: String,
    val downloadCount: Int,
    val visibility: String,
    val cohortIds: List<Long>,
    val pageCount: Int?,
    val isMine: Boolean,
)

/** 저장공간(67 바). */
data class StorageUsageResponse(
    val usedBytes: Long,
    val usedLabel: String,
    val quotaBytes: Long,
    val quotaLabel: String,
    val percent: Int,
)

data class DownloadUrlResponse(val downloadUrl: String, val fileName: String)
