package com.damoim.server.billing

import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.util.X509CertUtils
import com.nimbusds.jwt.SignedJWT
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey

/**
 * Apple StoreKit 2 JWS 서명 트랜잭션 검증(JDK 크립토 + Nimbus JOSE). 순수 로직(스프링 무관, 테스트 용이).
 *
 * ① JWS 헤더의 x5c 인증서 체인을 **설정된 신뢰 루트(Apple 루트 CA)** 까지 PKIX로 검증
 *    — x5c에 포함된 루트는 신뢰하지 않고, 반드시 서버가 소유한 신뢰 루트로 이어져야 통과(위조 서명 차단).
 * ② 리프 인증서 공개키로 ES256 서명 검증.
 * ③ payload 클레임(bundleId 일치·productId·만료) 확인.
 */
object AppStoreJwsVerifier {

    data class Payload(val productId: String, val bundleId: String, val expiresMillis: Long?)

    class InvalidReceiptException(message: String) : RuntimeException(message)

    fun verify(jws: String, trustedRoots: List<X509Certificate>, expectedBundleId: String): Payload {
        require(trustedRoots.isNotEmpty()) { "신뢰 루트 인증서가 설정되지 않았습니다." }

        val signed = runCatching { SignedJWT.parse(jws) }
            .getOrElse { throw InvalidReceiptException("JWS 형식이 올바르지 않습니다.") }

        val chain = signed.header.x509CertChain
            ?.mapNotNull { X509CertUtils.parse(it.decode()) }
            ?.takeIf { it.isNotEmpty() }
            ?: throw InvalidReceiptException("x5c 인증서 체인이 없습니다.")

        validateChain(chain, trustedRoots)

        val pub = chain.first().publicKey as? ECPublicKey
            ?: throw InvalidReceiptException("리프 공개키가 EC가 아닙니다.")
        if (!signed.verify(ECDSAVerifier(pub))) throw InvalidReceiptException("JWS 서명 검증 실패.")

        val claims = signed.jwtClaimsSet
        val bundleId = claims.getStringClaim("bundleId")
            ?: throw InvalidReceiptException("bundleId 클레임이 없습니다.")
        if (bundleId != expectedBundleId) throw InvalidReceiptException("bundleId 불일치.")
        val productId = claims.getStringClaim("productId")
            ?: throw InvalidReceiptException("productId 클레임이 없습니다.")
        val expires = runCatching { claims.getLongClaim("expiresDate") }.getOrNull()
        return Payload(productId, bundleId, expires)
    }

    /** x5c 리프→중간 체인이 신뢰 루트로 이어지는지 PKIX 검증. x5c에 담긴 자기서명 루트는 경로에서 제외. */
    private fun validateChain(chain: List<X509Certificate>, roots: List<X509Certificate>) {
        val path = chain.filterNot { it.subjectX500Principal == it.issuerX500Principal }.ifEmpty { chain }
        val certPath = CertificateFactory.getInstance("X.509").generateCertPath(path)
        val anchors = roots.map { TrustAnchor(it, null) }.toSet()
        val params = PKIXParameters(anchors).apply { isRevocationEnabled = false }
        runCatching { CertPathValidator.getInstance("PKIX").validate(certPath, params) }
            .onFailure { throw InvalidReceiptException("인증서 체인 검증 실패: ${it.message}") }
    }
}
