package com.damoim.server.billing

import com.damoim.server.common.BadRequestException
import com.damoim.server.common.ForbiddenException
import com.damoim.server.domain.enums.PlanTier
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** App Store 결제 증빙(JWS) 검증기 — 검증된 productId 반환, 실패 시 예외. 테스트에서 대체 가능하도록 인터페이스. */
interface AppleReceiptVerifier {
    fun verify(jws: String): String
}

@Component
class AppStoreReceiptVerifier(private val props: BillingProperties) : AppleReceiptVerifier {
    private val roots: List<X509Certificate> by lazy { loadRoots(props.apple.rootCertPath) }

    override fun verify(jws: String): String {
        if (roots.isEmpty() || props.apple.bundleId.isBlank()) {
            throw AppStoreJwsVerifier.InvalidReceiptException("App Store 검증이 구성되지 않았습니다(루트 인증서/bundleId).")
        }
        return AppStoreJwsVerifier.verify(jws, roots, props.apple.bundleId).productId
    }

    private fun loadRoots(path: String): List<X509Certificate> {
        val f = path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() } ?: return emptyList()
        val cf = CertificateFactory.getInstance("X.509")
        return f.inputStream().use { cf.generateCertificates(it).filterIsInstance<X509Certificate>() }
    }
}

/**
 * 구독 결제 증빙 검증 관문. [BillingProperties.verifyPurchases]=false면 개발용으로 통과(현행),
 * true면 유효한 증빙 필수(fail-closed) — StoreKit/Play 우회 후 subscribe 직접 호출 무료 구독을 차단.
 */
@Component
class PurchaseVerifier(
    private val props: BillingProperties,
    private val appleVerifier: AppleReceiptVerifier,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 검증 실패 시 예외를 던져 구독 활성화를 막는다(fail-closed). */
    fun verify(platform: String?, productId: String?, token: String?, tier: PlanTier) {
        if (!props.verifyPurchases) {
            log.warn("결제 증빙 검증 비활성(app.billing.verify-purchases=false) — 개발 전용. 운영은 반드시 true.")
            return
        }
        if (token.isNullOrBlank()) throw ForbiddenException("결제 증빙이 필요합니다.", "PURCHASE_PROOF_REQUIRED")
        val expected = props.productIdFor(tier) ?: throw BadRequestException("유효한 플랜이 아닙니다.", "INVALID_PLAN")
        if (productId != expected) throw ForbiddenException("상품이 플랜과 일치하지 않습니다.", "PRODUCT_MISMATCH")
        when (platform) {
            "APP_STORE" -> {
                val verified = runCatching { appleVerifier.verify(token) }
                    .getOrElse { throw ForbiddenException("결제 증빙 검증에 실패했습니다.", "PURCHASE_UNVERIFIED") }
                if (verified != expected) throw ForbiddenException("검증된 상품이 플랜과 다릅니다.", "PRODUCT_MISMATCH")
            }
            // Android 실결제(Play Billing)는 클라 미구현 → Play Developer API 검증 구성 전까지 fail-closed.
            "PLAY" -> throw ForbiddenException("Play 결제 검증이 아직 구성되지 않았습니다.", "PLAY_NOT_CONFIGURED")
            else -> throw ForbiddenException("지원하지 않는 결제 플랫폼입니다.", "UNSUPPORTED_PLATFORM")
        }
    }
}
