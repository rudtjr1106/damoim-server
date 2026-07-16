package com.damoim.server.storage

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 로컬 개발용 스토리지 — AWS 자격증명 없이 미디어 업로드/다운로드를 **실제로** 완성한다
 * (전엔 presign만 발급하고 저장/서빙은 안 하는 스텁이라 클라 PUT이 404로 실패했음).
 * 바이트는 [LocalStorage]가 디스크(app.storage.local.dir, 미설정 시 tmp)에 보관하고 [LocalStorageController]가 서빙한다.
 * S3 설정(app.storage.provider=s3) 시 [S3StorageService]로 대체된다. 기본값(미설정)도 로컬.
 *
 * presigned URL의 host/scheme는 **요청이 들어온 주소에서 파생**한다 — localhost 하드코딩을 없애
 * 실기기(Tailscale/LAN 등)에서도 도달 가능하게 한다. (Tailscale 등 프록시 뒤에선 X-Forwarded-*를
 * 존중하도록 application-local.yml 의 forward-headers-strategy=framework 가 필요하다.)
 */
@Service
@ConditionalOnProperty(name = ["app.storage.provider"], havingValue = "local", matchIfMissing = true)
class LocalStorageService(private val props: StorageProperties) : StorageService {

    // 저장 루트를 설정값(app.storage.local.dir)으로 확정한다. 부팅 시 1회 — 컨트롤러는 요청 시점에만
    // LocalStorage를 만지므로 순서 안전. 디렉터리를 미리 만들어 권한 문제를 부팅에서 드러낸다(fail-fast).
    init {
        LocalStorage.configureRoot(props.local.dir)
    }

    override val verifiesSize = true

    override fun objectSizeOrNull(key: String): Long? =
        LocalStorage.resolve(key).takeIf { Files.exists(it) }?.let { Files.size(it) }

    override fun presignUpload(key: String, contentType: String?): PresignedUpload =
        PresignedUpload("${baseUrl()}/_localstorage/$key?op=put", key, props.presignExpirySeconds)

    override fun presignDownload(key: String, downloadFileName: String): String =
        "${baseUrl()}/_localstorage/$key?op=get&name=$downloadFileName"

    override fun presignView(key: String): String =
        "${baseUrl()}/_localstorage/$key?op=view"

    override fun delete(key: String) {
        runCatching { Files.deleteIfExists(LocalStorage.resolve(key)) }
    }

    override fun listObjects(prefix: String): List<StoredObject> = emptyList()  // 로컬은 orphan 스윕 비대상

    /**
     * presigned URL의 베이스. 명시 설정(STORAGE_LOCAL_BASE_URL)이 있으면 그걸, 없으면 현재 요청의
     * scheme://host(:port)를 쓴다(프록시 뒤에선 X-Forwarded-* 반영). 요청 밖이면 localhost 폴백.
     */
    private fun baseUrl(): String {
        props.local.baseUrl.takeIf { it.isNotBlank() }?.let { return it.trimEnd('/') }
        return runCatching { ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() }
            .getOrNull()?.takeIf { it.isNotBlank() }
            ?: "http://localhost:8080"
    }
}

/**
 * 로컬 스토리지 바이트 저장소. 경로 탈출(`..`)을 차단한다.
 * 루트는 app.storage.local.dir로 재정의(미설정=tmp 디렉터리 — 기존 동작).
 */
object LocalStorage {
    private val defaultRoot: Path =
        Paths.get(System.getProperty("java.io.tmpdir"), "damoim-localstorage").toAbsolutePath().normalize()

    @Volatile
    private var root: Path = defaultRoot

    /** 부팅 시 [LocalStorageService]가 1회 호출. 빈 값이면 tmp 기본값(기존 동작) 유지. */
    fun configureRoot(dir: String) {
        root = if (dir.isBlank()) defaultRoot else Paths.get(dir).toAbsolutePath().normalize()
        Files.createDirectories(root)
    }

    fun resolve(key: String): Path {
        val target = root.resolve(key).normalize()
        require(target.startsWith(root)) { "잘못된 스토리지 키입니다." }
        return target
    }
}
