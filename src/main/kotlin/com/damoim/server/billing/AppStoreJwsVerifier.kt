package com.damoim.server.billing

import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.util.X509CertUtils
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.time.Duration
import java.time.Instant

/**
 * Apple StoreKit 2 JWS 서명 트랜잭션 검증(JDK 크립토 + Nimbus JOSE). 순수 로직(스프링 무관, 테스트 용이).
 *
 * ① JWS 헤더의 x5c 인증서 체인을 **설정된 신뢰 루트(Apple 루트 CA)** 까지 PKIX로 검증
 *    — x5c에 포함된 루트는 신뢰하지 않고, 반드시 서버가 소유한 신뢰 루트로 이어져야 통과(위조 서명 차단).
 * ② 리프 인증서 공개키로 ES256 서명 검증.
 * ③ payload 클레임(bundleId 일치·environment 일치·productId·transactionId·미만료) 확인.
 */
object AppStoreJwsVerifier {

    /** Apple `environment` 클레임 값. 샌드박스 영수증도 Apple이 정식 서명하므로 서명 검증만으로는 못 거른다. */
    const val ENV_PRODUCTION = "Production"
    const val ENV_SANDBOX = "Sandbox"

    /** 만료 판정 여유 — 서버/Apple 시계 오차만 감안한 최소치(길게 잡으면 만료 영수증이 그만큼 더 통과한다). */
    val CLOCK_SKEW: Duration = Duration.ofMinutes(5)

    /**
     * [transactionId]는 결제 1건의 고유 ID — 영수증 재사용 차단(PurchaseLedger)의 키다.
     * [expiresAt]은 구독 만료 시각(구독이 아닌 트랜잭션엔 없음) — 상위에서 다음 결제일로 쓴다.
     */
    data class Payload(
        val productId: String,
        val transactionId: String,
        val bundleId: String,
        val environment: String,
        val expiresAt: Instant?,
    )

    class InvalidReceiptException(message: String) : RuntimeException(message)

    fun verify(
        jws: String,
        trustedRoots: List<X509Certificate>,
        expectedBundleId: String,
        allowedEnvironments: Set<String>,
        now: Instant = Instant.now(),
    ): Payload {
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

        return readClaims(signed.jwtClaimsSet, expectedBundleId, allowedEnvironments, now)
    }

    /**
     * payload 클레임 검증 — 크립토와 분리해 두어(테스트가 서명 없이 클레임 규칙만 검증) 규칙 추가가 쉽다.
     * 클레임이 없는 경우는 모두 거부한다: "없으니 통과"는 곧 우회 경로다.
     */
    internal fun readClaims(
        claims: JWTClaimsSet,
        expectedBundleId: String,
        allowedEnvironments: Set<String>,
        now: Instant,
    ): Payload {
        // 허용 환경이 비면 무엇과 대조해야 할지 모른다 = 전부 통과 → fail-closed로 거부.
        if (allowedEnvironments.isEmpty()) throw InvalidReceiptException("허용 App Store 환경이 설정되지 않았습니다.")

        val bundleId = claims.getStringClaim("bundleId")
            ?: throw InvalidReceiptException("bundleId 클레임이 없습니다.")
        if (bundleId != expectedBundleId) throw InvalidReceiptException("bundleId 불일치.")
        // 샌드박스(테스트 계정) 영수증이 운영에서 통과하면 결제 없이 유료 플랜이 켜진다 — 반드시 대조.
        val environment = claims.getStringClaim("environment")
            ?: throw InvalidReceiptException("environment 클레임이 없습니다.")
        if (allowedEnvironments.none { it.equals(environment, ignoreCase = true) }) {
            throw InvalidReceiptException("허용되지 않은 App Store 환경입니다: $environment")
        }
        val productId = claims.getStringClaim("productId")
            ?: throw InvalidReceiptException("productId 클레임이 없습니다.")
        // StoreKit 2 서명 트랜잭션에는 항상 존재 — 없으면 재사용 차단 키가 없다는 뜻이라 통과시키지 않는다.
        val transactionId = claims.getStringClaim("transactionId")
            ?: throw InvalidReceiptException("transactionId 클레임이 없습니다.")
        val expiresAt = runCatching { claims.getLongClaim("expiresDate") }.getOrNull()
            ?.let(Instant::ofEpochMilli)
        // 구독이 아닌 트랜잭션엔 expiresDate가 없다 — 있는데 이미 지난 영수증만 거부(시계 오차만 여유).
        if (expiresAt != null && !expiresAt.plus(CLOCK_SKEW).isAfter(now)) {
            throw InvalidReceiptException("만료된 영수증입니다.")
        }
        return Payload(productId, transactionId, bundleId, environment, expiresAt)
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
