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

    /** 프리픽스 아래 오브젝트 목록(orphan 스윕용). 로컬 스텁은 빈 목록. */
    fun listObjects(prefix: String): List<StoredObject>
}

data class PresignedUpload(val url: String, val key: String, val expiresInSeconds: Long)

/** 스토리지 오브젝트 메타(orphan 판정용). */
data class StoredObject(val key: String, val lastModifiedEpochMillis: Long)

@ConfigurationProperties(prefix = "app.storage")
data class StorageProperties(
    val provider: String,
    val quotaBytes: Long,
    val presignExpirySeconds: Long,
    val s3: S3Props,
    val orphanSweep: OrphanSweep = OrphanSweep(),
    val local: LocalProps = LocalProps(),
) {
    data class S3Props(val bucket: String, val region: String)

    /**
     * 로컬 스토리지(provider=local) 설정. [baseUrl]이 비어 있으면 presigned URL의 host/scheme를
     * 요청에서 파생한다. 프록시(Tailscale 등) 뒤에서 파생이 어긋나면 STORAGE_LOCAL_BASE_URL로
     * 명시(예: https://xxx.ts.net).
     *
     * [dir]는 바이트 저장 루트. 비우면 java.io.tmpdir/damoim-localstorage(기존 동작 = 로컬 개발 기본값).
     * 컨테이너 배포에선 반드시 영구 볼륨 경로를 STORAGE_LOCAL_DIR로 주입할 것 —
     * 안 하면 컨테이너 재생성마다 업로드된 파일이 전부 사라진다(DB의 key만 남아 GET 404).
     */
    data class LocalProps(val baseUrl: String = "", val dir: String = "")

    /** orphan 스윕 배치 설정. [graceHours]는 업로드 후 등록 대기 중인 오브젝트를 오삭제하지 않기 위한 유예. */
    data class OrphanSweep(
        val enabled: Boolean = false,
        val graceHours: Long = 24,
        val cron: String = "0 0 4 * * *",
    )
}

/** 스토리지 오브젝트 키 생성. 도메인별 프리픽스로 소유권 검증(크로스테넌트 차단)에 사용. */
object StorageKeys {
    // 스토리지 키는 URL 경로에 그대로 실려 인코딩/디코딩 경계를 넘나든다. 비ASCII(한글)를 키에 남기면
    // presigned URL의 인코딩과 서버가 읽는 키가 어긋나 업로드 검증이 깨지므로(UPLOAD_INCOMPLETE),
    // 키는 ASCII만 허용해 provider(local/S3) 무관하게 인코딩 차이가 생길 여지를 없앤다.
    // 사용자에게 보이는 원본 파일명은 DB(fileName)에 그대로 보관되고 다운로드 시 Content-Disposition으로
    // 복원되므로 UX 손실은 없다. 키 경로에 UUID 디렉터리가 있어 이름 충돌도 없다.
    // (기존에 만들어진 한글 키 오브젝트는 그대로 남고 읽기도 계속 동작한다 — 키 형식만 앞으로 ASCII.)
    private val UNSAFE = Regex("[^A-Za-z0-9._-]")

    // 점만 남는 이름(".", "..")은 경로 세그먼트로서 디렉터리를 가리키므로 "file"로 대체한다.
    private fun sanitize(fileName: String): String =
        fileName.replace(UNSAFE, "_").takeLast(120)
            .let { if (it.isBlank() || it.all { c -> c == '.' }) "file" else it }

    /** 자료실: resources/{clubId}/{uuid}/{name}. */
    fun forResource(clubId: Long, fileName: String): String =
        "resources/$clubId/${UUID.randomUUID()}/${sanitize(fileName)}"

    /** 게시판 첨부: posts/{clubId}/{uuid}/{name}. */
    fun forPost(clubId: Long, fileName: String): String =
        "posts/$clubId/${UUID.randomUUID()}/${sanitize(fileName)}"

    /** 프로필 사진: profiles/{userId}/{uuid}/{name}. */
    fun forProfile(userId: Long, fileName: String): String =
        "profiles/$userId/${UUID.randomUUID()}/${sanitize(fileName)}"

    /** 동아리 대표 이미지: clubs/{clubId}/{uuid}/{name}. */
    fun forClub(clubId: Long, fileName: String): String =
        "clubs/$clubId/${UUID.randomUUID()}/${sanitize(fileName)}"
}
