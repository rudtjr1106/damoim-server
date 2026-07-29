package com.damoim.server.billing

import com.damoim.server.common.BadRequestException
import com.damoim.server.common.ForbiddenException
import com.damoim.server.domain.enums.PlanTier
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * 스토어에서 검증에 성공한 결제 증빙. [transactionId]는 스토어가 발급한 트랜잭션 고유 ID로,
 * 영수증 재사용(같은 결제로 여러 동아리 구독)을 막는 키다.
 */
data class VerifiedReceipt(val productId: String, val transactionId: String)

/** App Store 결제 증빙(JWS) 검증기 — 검증 결과 반환, 실패 시 예외. 테스트에서 대체 가능하도록 인터페이스. */
interface AppleReceiptVerifier {
    fun verify(jws: String): VerifiedReceipt
}

@Component
class AppStoreReceiptVerifier(private val props: BillingProperties) : AppleReceiptVerifier {
    private val roots: List<X509Certificate> by lazy { loadRoots(props.apple.rootCertPath) }

    override fun verify(jws: String): VerifiedReceipt {
        if (roots.isEmpty() || props.apple.bundleId.isBlank()) {
            throw AppStoreJwsVerifier.InvalidReceiptException("App Store 검증이 구성되지 않았습니다(루트 인증서/bundleId).")
        }
        val payload = AppStoreJwsVerifier.verify(jws, roots, props.apple.bundleId)
        return VerifiedReceipt(payload.productId, payload.transactionId)
    }

    private fun loadRoots(path: String): List<X509Certificate> {
        val f = path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() } ?: return emptyList()
        val cf = CertificateFactory.getInstance("X.509")
        return f.inputStream().use { cf.generateCertificates(it).filterIsInstance<X509Certificate>() }
    }
}

/**
 * 구독 결제 증빙 검증 관문. 유효한 증빙이 없으면 예외를 던져 구독 활성화를 막는다(fail-closed) —
 * StoreKit/Play를 우회한 subscribe 직접 호출로 유료 플랜이 켜지는 구멍을 닫는다.
 *
 * 검증 비활성([BillingProperties.verifyPurchases]=false)은 **로컬/개발 전용**이다.
 * 운영(prod)에서는 그냥 통과시키면 결제 없이 PRO가 켜지므로(fail-open), 구독 요청 자체를 거부한다.
 */
@Component
class PurchaseVerifier(
    private val props: BillingProperties,
    private val appleVerifier: AppleReceiptVerifier,
    private val playVerifier: PlayReceiptVerifier,
    private val ledger: PurchaseLedger,
    environment: Environment,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 운영 프로파일 여부 — 검증 비활성 시 '통과'가 아니라 '거부'로 갈린다. 프로파일은 런타임 중 안 바뀌므로 1회 계산. */
    private val prodProfile: Boolean = environment.matchesProfiles(PROD_PROFILE)

    /** 검증 실패 시 예외를 던져 구독 활성화를 막는다(fail-closed). */
    fun verify(platform: String?, productId: String?, token: String?, tier: PlanTier) {
        if (!props.verifyPurchases) {
            // application-prod.yml이 BILLING_VERIFY 기본값을 true로 올려두지만, 운영자가 실수로 false를
            // 주입해도 운영에서는 무검증 통과를 허용하지 않는다(2중 방어). 개발/로컬은 기존대로 통과.
            if (prodProfile) {
                log.error("운영에서 결제 증빙 검증이 비활성(app.billing.verify-purchases=false) — 구독 요청을 거부합니다.")
                throw ForbiddenException("결제 검증이 구성되지 않아 구독을 처리할 수 없습니다.", "BILLING_NOT_CONFIGURED")
            }
            log.warn("결제 증빙 검증 비활성(app.billing.verify-purchases=false) — 개발 전용. 운영은 반드시 true.")
            return
        }
        if (token.isNullOrBlank()) throw ForbiddenException("결제 증빙이 필요합니다.", "PURCHASE_PROOF_REQUIRED")
        val expected = props.productIdFor(tier) ?: throw BadRequestException("유효한 플랜이 아닙니다.", "INVALID_PLAN")
        if (productId != expected) throw ForbiddenException("상품이 플랜과 일치하지 않습니다.", "PRODUCT_MISMATCH")
        val store = platform?.trim().orEmpty()
        val receipt = when (store) {
            "APP_STORE" -> runCatching { appleVerifier.verify(token) }
                .getOrElse { throw ForbiddenException("결제 증빙 검증에 실패했습니다.", "PURCHASE_UNVERIFIED") }
            // Android 실결제(Play Billing)는 서비스계정 키가 붙어야 검증 가능 — 미구성이면 현행대로 거부.
            "PLAY" -> try {
                playVerifier.verify(expected, token)
            } catch (e: PlayNotConfiguredException) {
                throw ForbiddenException("Play 결제 검증이 아직 구성되지 않았습니다.", "PLAY_NOT_CONFIGURED")
            } catch (e: Exception) {
                throw ForbiddenException("결제 증빙 검증에 실패했습니다.", "PURCHASE_UNVERIFIED")
            }
            else -> throw ForbiddenException("지원하지 않는 결제 플랫폼입니다.", "UNSUPPORTED_PLATFORM")
        }
        if (receipt.productId != expected) throw ForbiddenException("검증된 상품이 플랜과 다릅니다.", "PRODUCT_MISMATCH")
        // 영수증 1건 = 구독 1회. 이미 소비된 트랜잭션이면 거부 — 같은 영수증을 여러 동아리에 돌려쓰는 우회 차단.
        if (!ledger.recordFirstUse(store, tier, receipt)) {
            throw ForbiddenException("이미 사용된 결제 증빙입니다.", "PURCHASE_ALREADY_USED")
        }
    }

    private companion object {
        const val PROD_PROFILE = "prod"
    }
}
