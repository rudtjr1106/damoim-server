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
class LocalStorageService(
    private val props: StorageProperties,
    private val signer: LocalStorageSigner,
) : StorageService {

    // 저장 루트를 설정값(app.storage.local.dir)으로 확정한다. 부팅 시 1회 — 컨트롤러는 요청 시점에만
    // LocalStorage를 만지므로 순서 안전. 디렉터리를 미리 만들어 권한 문제를 부팅에서 드러낸다(fail-fast).
    init {
        LocalStorage.configureRoot(props.local.dir)
    }

    override val verifiesSize = true

    override fun objectSizeOrNull(key: String): Long? =
        LocalStorage.resolve(key).takeIf { Files.exists(it) }?.let { Files.size(it) }

    // S3 presigned처럼 만료·HMAC 서명을 붙인다(무인증 capability URL 방지).
    // 서명은 연산([StorageOp])에 한정된다 — 조회용으로 발급한 URL의 sig로는 업로드(PUT)가 불가능하다.
    // ⚠️ op가 서명에 들어가면서 이 변경 이전에 발급된 URL은 모두 403이 된다(의도된 무효화).
    private fun sig(op: StorageOp, key: String) = signer.signedParams(op, key, props.presignExpirySeconds)

    override fun presignUpload(key: String, contentType: String?): PresignedUpload =
        PresignedUpload("${baseUrl()}/_localstorage/$key?op=put&${sig(StorageOp.PUT, key)}", key, props.presignExpirySeconds)

    // 다운로드·인라인 뷰는 둘 다 HTTP GET이므로 같은 op로 서명한다(URL의 op 파라미터는 표시용 힌트).
    override fun presignDownload(key: String, downloadFileName: String): String =
        "${baseUrl()}/_localstorage/$key?op=get&name=$downloadFileName&${sig(StorageOp.GET, key)}"

    override fun presignView(key: String): String =
        "${baseUrl()}/_localstorage/$key?op=view&${sig(StorageOp.GET, key)}"

    override fun delete(key: String) {
        runCatching {
            val target = LocalStorage.resolve(key)
            Files.deleteIfExists(target)
            LocalStorage.pruneEmptyParents(target)
        }
    }

    /**
     * 프리픽스 아래 실제 파일 목록(orphan 스윕용). 전엔 빈 목록이라 provider=local에선 회수 경로가
     * 아예 없었다 — 자가호스팅 운영 기본값이 local이라 지운 첨부·교체된 프로필 사진의 바이트가
     * 영원히 쌓였다. S3의 ListObjectsV2와 같은 계약(키=루트 기준 상대경로)으로 채운다.
     */
    override fun listObjects(prefix: String): List<StoredObject> = LocalStorage.list(prefix)

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

    /**
     * 삭제 후 남은 빈 상위 디렉터리를 루트 직전까지 걷어낸다. 키가 `prefix/{id}/{uuid}/{파일}`이라
     * 파일만 지우면 업로드 1건당 빈 uuid 디렉터리가 영구히 남는다(스윕이 돌수록 쌓임).
     * 비어있지 않은 디렉터리는 삭제가 실패하므로(DirectoryNotEmptyException) 형제 파일은 안전하다.
     */
    fun pruneEmptyParents(deleted: Path) {
        var dir = deleted.parent
        while (dir != null && dir != root && dir.startsWith(root)) {
            if (!runCatching { Files.deleteIfExists(dir) }.getOrDefault(false)) return
            dir = dir.parent
        }
    }

    /**
     * [prefix] 이하의 실제 파일 전부(키 = 루트 기준 상대경로, S3 키와 동일 형태). orphan 스윕용.
     * 프리픽스 디렉터리가 아직 없으면 빈 목록(업로드가 한 번도 없던 프리픽스 — 정상).
     * 키 구분자는 항상 '/'로 정규화한다 — DB에 저장된 키와 문자열 비교로 참조 여부를 판정하기 때문.
     */
    fun list(prefix: String): List<StoredObject> {
        val base = resolve(prefix.trimEnd('/'))
        if (!Files.isDirectory(base)) return emptyList()
        val out = mutableListOf<StoredObject>()
        Files.walk(base).use { paths ->
            paths.filter { Files.isRegularFile(it) }.forEach { path ->
                val key = root.relativize(path).joinToString("/") { it.toString() }
                out += StoredObject(key, Files.getLastModifiedTime(path).toMillis())
            }
        }
        return out
    }
}
