package com.damoim.server.storage

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * presigned URL이 허용하는 연산 종류. **서명 입력에 포함**되므로 한 연산의 서명을 다른 연산에
 * 재사용할 수 없다(S3 presigned가 HTTP 메서드를 서명에 넣는 것과 같은 이유).
 *
 * 조회 URL은 이미지 표시용으로 클라 곳곳에 배포되는데, op가 서명에 없던 시절엔 그 `sig`를 그대로
 * PUT에 붙여 남의 프로필 사진·동아리 로고·게시판 첨부 바이트를 덮어쓸 수 있었다.
 * 다운로드/인라인 뷰는 둘 다 HTTP GET이므로 [GET] 하나로 묶는다(컨트롤러가 HTTP 메서드로 판정).
 */
enum class StorageOp(val value: String) { PUT("put"), GET("get") }

/**
 * 로컬 스토리지(provider=local) presigned URL 서명·검증. S3 presigned URL이 서명·만료로 접근을
 * 제한하듯, 로컬 서빙 경로(_localstorage)도 `exp`(만료)+`sig`(HMAC-SHA256)를 요구해 **무인증·무만료 capability
 * URL 노출**을 막는다. 키를 알아도 유효 서명·미만료가 아니면 403.
 *
 * 서명 대상은 `op|key|exp` — 연산(put/get)까지 묶어야 조회 서명으로 업로드(덮어쓰기)를 할 수 없다.
 * ⚠️ 서명 입력에 op가 추가되면서 **이 변경 이전에 발급된 URL은 전부 무효(403)가 된다** — 의도된 것이다.
 * 클라는 만료 시 URL을 재발급받는 흐름이므로 재요청하면 정상화되고, 서버에 남은 바이트는 영향 없다.
 *
 * 서명 시크릿은 `STORAGE_LOCAL_SIGN_SECRET`(app.storage.local.sign-secret)로 주입한다.
 *
 * 시크릿 결정([resolveSecret]) — [com.damoim.server.security.JwtTokenProvider]와 같은 규칙:
 *  - 제공 + 32바이트 이상 → 그 시크릿 사용.
 *  - 미제공(blank) & prod 프로파일 → 부팅 실패(fail-fast). 랜덤 폴백을 운영에서 허용하면 재시작마다
 *    시크릿이 바뀌어 **이미 배포된 presigned URL이 전부 403**이 된다(사진이 통째로 안 보임).
 *  - 미제공 & 비-prod → 부팅마다 랜덤(재시작 시 기존 URL 무효 — 개발엔 무해). 커밋된 알려진 키가
 *    없어 위조는 불가능하다.
 */
@Component
@ConditionalOnProperty(name = ["app.storage.provider"], havingValue = "local", matchIfMissing = true)
class LocalStorageSigner(props: StorageProperties, environment: Environment) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val secret: ByteArray = resolveSecret(props.local.signSecret, environment)

    private fun resolveSecret(signSecret: String, env: Environment): ByteArray {
        if (signSecret.isBlank()) {
            val isProd = env.activeProfiles.contains("prod")
            check(!isProd) {
                "app.storage.local.sign-secret(STORAGE_LOCAL_SIGN_SECRET)은 운영에서 필수입니다. " +
                    "미주입 시 재시작마다 서명 키가 바뀌어 기존 파일 URL이 전부 403이 됩니다. " +
                    "`openssl rand -base64 48`로 생성한 고정값을 주입하세요."
            }
            log.warn("app.storage.local.sign-secret 미설정 — 개발용 임시 랜덤 시크릿을 생성합니다(재시작 시 기존 URL 무효). 운영 금지.")
            return ByteArray(32).also { SecureRandom().nextBytes(it) }
        }
        val bytes = signSecret.toByteArray(Charsets.UTF_8)
        require(bytes.size >= MIN_SECRET_BYTES) {
            "app.storage.local.sign-secret must be at least $MIN_SECRET_BYTES bytes for HMAC-SHA256."
        }
        return bytes
    }

    /** [op]에 한정된 `exp`(만료 epoch초)와 `sig`(hex HMAC)를 쿼리스트링 조각으로 반환. */
    fun signedParams(op: StorageOp, key: String, ttlSeconds: Long): String {
        val exp = Instant.now().epochSecond + ttlSeconds
        return "exp=$exp&sig=${hmac(op, key, exp)}"
    }

    /**
     * 요청의 exp/sig가 **해당 op에 대해** 유효하고 미만료면 true.
     * [op]는 반드시 처리 중인 HTTP 메서드에서 정하고, 요청 파라미터에서 읽지 말 것 —
     * 공격자가 고를 수 있으면 op를 서명에 넣은 의미가 사라진다.
     */
    fun isValid(op: StorageOp, key: String, exp: Long?, sig: String?): Boolean {
        if (exp == null || sig == null) return false
        if (exp < Instant.now().epochSecond) return false
        // 상수시간 비교로 타이밍 공격 방지.
        return MessageDigest.isEqual(hmac(op, key, exp).toByteArray(Charsets.UTF_8), sig.toByteArray(Charsets.UTF_8))
    }

    // 구분자 '|'는 키 화이트리스트(LocalStorageController.KEY)에 없고 op/exp도 고정 형식이라
    // 필드 경계가 모호해질 여지가 없다(서명 대상 문자열 혼동 방지).
    private fun hmac(op: StorageOp, key: String, exp: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal("${op.value}|$key|$exp".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        /** SHA-256 출력 길이(=권장 키 강도). JWT 시크릿과 같은 하한을 요구해 약한 키를 배제한다. */
        const val MIN_SECRET_BYTES = 32
    }
}
