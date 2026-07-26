package com.damoim.server.storage

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 로컬 스토리지(provider=local) presigned URL 서명·검증. S3 presigned URL이 서명·만료로 접근을
 * 제한하듯, 로컬 서빙 경로(_localstorage)도 `exp`(만료)+`sig`(HMAC-SHA256)를 요구해 **무인증·무만료 capability
 * URL 노출**을 막는다. 키를 알아도 유효 서명·미만료가 아니면 403.
 *
 * 서명 시크릿은 `STORAGE_LOCAL_SIGN_SECRET`(app.storage.local.sign-secret)로 주입한다. 미설정이면
 * 부팅마다 랜덤 시크릿을 생성한다 — 재시작 시 기존 URL이 무효화되지만(로컬 개발엔 무해), 커밋된
 * 알려진 키가 없어 위조가 불가능하다. **자가호스팅 운영에선 반드시 고정 시크릿을 주입**할 것.
 */
@Component
@ConditionalOnProperty(name = ["app.storage.provider"], havingValue = "local", matchIfMissing = true)
class LocalStorageSigner(props: StorageProperties) {

    private val secret: ByteArray = props.local.signSecret
        .takeIf { it.isNotBlank() }?.toByteArray(Charsets.UTF_8)
        ?: ByteArray(32).also { SecureRandom().nextBytes(it) }

    /** `exp`(만료 epoch초)와 `sig`(hex HMAC)를 쿼리스트링 조각으로 반환. */
    fun signedParams(key: String, ttlSeconds: Long): String {
        val exp = Instant.now().epochSecond + ttlSeconds
        return "exp=$exp&sig=${hmac(key, exp)}"
    }

    /** 요청의 exp/sig가 유효하고 미만료면 true. */
    fun isValid(key: String, exp: Long?, sig: String?): Boolean {
        if (exp == null || sig == null) return false
        if (exp < Instant.now().epochSecond) return false
        // 상수시간 비교로 타이밍 공격 방지.
        return MessageDigest.isEqual(hmac(key, exp).toByteArray(Charsets.UTF_8), sig.toByteArray(Charsets.UTF_8))
    }

    private fun hmac(key: String, exp: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal("$key|$exp".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
