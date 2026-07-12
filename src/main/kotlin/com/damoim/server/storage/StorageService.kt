package com.damoim.server.storage

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.UUID

/**
 * 파일 스토리지 추상화. presigned URL로 클라가 S3에 직접 업로드/다운로드하고,
 * 서버는 URL 발급·메타데이터만 다룬다(바이트 미경유). 로컬 개발은 스텁 구현으로 대체.
 */
interface StorageService {
    /** 실제 업로드 크기 검증 가능 여부(S3=true / 로컬스텁=false). */
    val verifiesSize: Boolean

    /** 오브젝트 실제 크기(바이트). 없으면 null(업로드 미완료). 로컬스텁은 항상 null. */
    fun objectSizeOrNull(key: String): Long?

    /** 업로드용 presigned PUT URL. */
    fun presignUpload(key: String, contentType: String?): PresignedUpload

    /** 다운로드용 presigned GET URL(파일명 지정, attachment). */
    fun presignDownload(key: String, downloadFileName: String): String

    /** 인라인 표시용 presigned GET URL(이미지 렌더 — attachment 강제 안 함). */
    fun presignView(key: String): String

    fun delete(key: String)
}

data class PresignedUpload(val url: String, val key: String, val expiresInSeconds: Long)

@ConfigurationProperties(prefix = "app.storage")
data class StorageProperties(
    val provider: String,
    val quotaBytes: Long,
    val presignExpirySeconds: Long,
    val s3: S3Props,
) {
    data class S3Props(val bucket: String, val region: String)
}

/** 스토리지 오브젝트 키 생성. 도메인별 프리픽스로 소유권 검증(크로스테넌트 차단)에 사용. */
object StorageKeys {
    private val UNSAFE = Regex("[^A-Za-z0-9._가-힣-]")

    private fun sanitize(fileName: String): String =
        fileName.replace(UNSAFE, "_").takeLast(120).ifBlank { "file" }

    /** 자료실: resources/{clubId}/{uuid}/{name}. */
    fun forResource(clubId: Long, fileName: String): String =
        "resources/$clubId/${UUID.randomUUID()}/${sanitize(fileName)}"

    /** 게시판 첨부: posts/{clubId}/{uuid}/{name}. */
    fun forPost(clubId: Long, fileName: String): String =
        "posts/$clubId/${UUID.randomUUID()}/${sanitize(fileName)}"

    /** 프로필 사진: profiles/{userId}/{uuid}/{name}. */
    fun forProfile(userId: Long, fileName: String): String =
        "profiles/$userId/${UUID.randomUUID()}/${sanitize(fileName)}"
}
