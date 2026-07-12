package com.damoim.server.storage

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * 로컬 개발용 스텁 — AWS 자격증명 없이 자료실 도메인 로직(권한·폴더·저장공간·기수공개)을
 * 완전히 검증하기 위한 것. 실제 업로드/다운로드는 되지 않으며, S3 설정(app.storage.provider=s3) 시
 * S3StorageService로 대체된다. 기본값(provider 미설정)도 로컬.
 */
@Service
@ConditionalOnProperty(name = ["app.storage.provider"], havingValue = "local", matchIfMissing = true)
class LocalStorageService(private val props: StorageProperties) : StorageService {

    override val verifiesSize = false                        // 스텁 — 실제 오브젝트 없음
    override fun objectSizeOrNull(key: String): Long? = null

    override fun presignUpload(key: String, contentType: String?): PresignedUpload =
        PresignedUpload("http://localhost:8080/_localstorage/$key?op=put", key, props.presignExpirySeconds)

    override fun presignDownload(key: String, downloadFileName: String): String =
        "http://localhost:8080/_localstorage/$key?op=get&name=$downloadFileName"

    override fun presignView(key: String): String =
        "http://localhost:8080/_localstorage/$key?op=view"

    override fun delete(key: String) { /* 스텁 — no-op */ }
}
