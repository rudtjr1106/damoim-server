package com.damoim.server.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * 리프레시 토큰 생성/해시. 토큰은 고엔트로피 랜덤이라 DB에는 SHA-256 해시만 저장한다(평문 금지).
 * (고엔트로피 랜덤이므로 bcrypt 불필요 — 무차별 대입이 불가능.)
 */
object RandomTokens {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    /** 256비트 URL-safe 랜덤 토큰(클라에 1회 전달, 서버 저장 안 함). */
    fun generate(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    /** 저장/조회용 SHA-256 16진 해시. */
    fun sha256(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
