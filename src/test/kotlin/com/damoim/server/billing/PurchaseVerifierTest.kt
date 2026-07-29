package com.damoim.server.billing

import com.damoim.server.common.BadRequestException
import com.damoim.server.common.ForbiddenException
import com.damoim.server.domain.enums.PlanTier
import org.springframework.mock.env.MockEnvironment
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * 결제 증빙 게이팅(fail-closed) 검증 — 크립토/DB 없이 순수 게이팅 로직만.
 * "verify-purchases=true면 유효한 증빙 없이는 구독 불가", "운영에서는 검증을 꺼도 구독 불가",
 * "같은 영수증은 두 번 못 쓴다"는 보안 속성이 성립함을 증명한다.
 */
class PurchaseVerifierTest {

    private val std = "com.damoim.app.subscription.standard"
    private val pro = "com.damoim.app.subscription.pro"
    private val props = BillingProperties(verifyPurchases = true, standardProductId = std, proProductId = pro)

    private fun okApple(product: String, txn: String = "txn-1") = object : AppleReceiptVerifier {
        override fun verify(jws: String) = VerifiedReceipt(product, txn)
    }
    private fun failApple() = object : AppleReceiptVerifier {
        override fun verify(jws: String): VerifiedReceipt = throw AppStoreJwsVerifier.InvalidReceiptException("bad")
    }

    /** Play는 서비스계정 키 미설정이 기본(현행) — 항상 미구성 예외. */
    private val playOff = object : PlayReceiptVerifier {
        override fun verify(productId: String, purchaseToken: String): VerifiedReceipt =
            throw PlayNotConfiguredException("not configured")
    }

    /** 인메모리 대장 — 같은 (platform, transactionId)는 한 번만 통과. */
    private class FakeLedger : PurchaseLedger {
        private val used = mutableSetOf<String>()
        override fun recordFirstUse(platform: String, tier: PlanTier, receipt: VerifiedReceipt): Boolean =
            used.add("$platform:${receipt.transactionId}")
    }

    private fun verifier(
        p: BillingProperties = props,
        apple: AppleReceiptVerifier = okApple(std),
        ledger: PurchaseLedger = FakeLedger(),
        prod: Boolean = false,
    ) = PurchaseVerifier(
        p,
        apple,
        playOff,
        ledger,
        MockEnvironment().apply { if (prod) setActiveProfiles("prod") },
    )

    @Test
    fun `검증 비활성(dev)이면 증빙 없어도 통과`() {
        verifier(props.copy(verifyPurchases = false), apple = failApple())
            .verify(null, null, null, PlanTier.STANDARD)  // 예외 없음
    }

    @Test
    fun `운영(prod)에서는 검증을 꺼도 통과가 아니라 거부`() {
        assertFailsWith<ForbiddenException> {
            verifier(props.copy(verifyPurchases = false), apple = failApple(), prod = true)
                .verify("APP_STORE", std, "jws", PlanTier.STANDARD)
        }
    }

    @Test
    fun `증빙 토큰이 없으면 거부`() {
        assertFailsWith<ForbiddenException> {
            verifier().verify("APP_STORE", std, null, PlanTier.STANDARD)
        }
    }

    @Test
    fun `상품ID가 플랜과 다르면 거부`() {
        assertFailsWith<ForbiddenException> {
            verifier().verify("APP_STORE", "wrong.product", "jws", PlanTier.STANDARD)
        }
    }

    @Test
    fun `App Store 서명 검증 실패면 거부`() {
        assertFailsWith<ForbiddenException> {
            verifier(apple = failApple()).verify("APP_STORE", std, "jws", PlanTier.STANDARD)
        }
    }

    @Test
    fun `검증된 상품이 기대 플랜과 다르면 거부`() {
        assertFailsWith<ForbiddenException> {
            verifier(apple = okApple(pro)).verify("APP_STORE", std, "jws", PlanTier.STANDARD)
        }
    }

    @Test
    fun `유효한 App Store 증빙이면 통과`() {
        verifier().verify("APP_STORE", std, "jws", PlanTier.STANDARD)  // 예외 없음
    }

    @Test
    fun `같은 영수증을 두 번 쓰면 거부(재사용 차단)`() {
        val ledger = FakeLedger()
        verifier(ledger = ledger).verify("APP_STORE", std, "jws", PlanTier.STANDARD)
        // 같은 트랜잭션 ID의 증빙 → 다른 동아리에서 재사용해도 대장에 이미 있으므로 거부
        assertFailsWith<ForbiddenException> {
            verifier(ledger = ledger).verify("APP_STORE", std, "jws", PlanTier.STANDARD)
        }
    }

    @Test
    fun `Play는 아직 미구성이라 거부`() {
        assertFailsWith<ForbiddenException> {
            verifier().verify("PLAY", std, "token", PlanTier.STANDARD)
        }
    }

    @Test
    fun `알 수 없는 플랫폼은 거부`() {
        assertFailsWith<ForbiddenException> {
            verifier().verify("UNKNOWN", std, "token", PlanTier.STANDARD)
        }
    }

    @Test
    fun `상품ID 매핑 없는 플랜(FREE)은 거부`() {
        assertFailsWith<BadRequestException> {
            verifier().verify("APP_STORE", std, "jws", PlanTier.FREE)
        }
    }
}
