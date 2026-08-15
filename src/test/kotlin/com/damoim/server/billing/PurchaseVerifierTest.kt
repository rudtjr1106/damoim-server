package com.damoim.server.billing

import com.damoim.server.common.BadRequestException
import com.damoim.server.common.ForbiddenException
import com.damoim.server.domain.enums.PlanTier
import com.nimbusds.jwt.JWTClaimsSet
import org.springframework.mock.env.MockEnvironment
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 결제 증빙 게이팅(fail-closed) 검증 — 크립토/DB 없이 순수 게이팅 로직만.
 * "verify-purchases=true면 유효한 증빙 없이는 구독 불가", "운영에서는 검증을 꺼도 구독 불가",
 * "같은 영수증은 두 번 못 쓴다"는 보안 속성이 성립함을 증명한다.
 */
class PurchaseVerifierTest {

    private val std = "com.damoim.app.subscription.standard"
    private val pro = "com.damoim.app.subscription.pro"
    private val props = BillingProperties(verifyPurchases = true, standardProductId = std, proProductId = pro)

    private fun okApple(product: String, txn: String = "txn-1", expiresAt: Instant? = null) =
        object : AppleReceiptVerifier {
            override fun verify(jws: String) = VerifiedReceipt(product, txn, expiresAt)
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

    // ── 영수증 만료 시각 전달(다음 갱신일의 근거) ──

    @Test
    fun `검증된 영수증의 만료 시각이 호출부로 전달된다`() {
        val expires = Instant.now().plusSeconds(30 * 86_400L)
        val receipt = verifier(apple = okApple(std, expiresAt = expires))
            .verify("APP_STORE", std, "jws", PlanTier.STANDARD)
        // SubscriptionService가 이 값을 nextBillingAt으로 쓴다(30일 상수 대신).
        assertEquals(expires, receipt?.expiresAt)
    }

    @Test
    fun `검증 비활성(dev)이면 증빙이 없으므로 null - 30일 폴백`() {
        val receipt = verifier(props.copy(verifyPurchases = false)).verify(null, null, null, PlanTier.STANDARD)
        assertNull(receipt)
    }

    // ── 허용 환경 결정(설정 없으면 프로파일 기본값) ──

    @Test
    fun `운영 기본은 Production만 허용`() {
        assertEquals(setOf("Production"), resolveAppleEnvironments("", prodProfile = true))
    }

    @Test
    fun `개발 기본은 Sandbox도 허용`() {
        assertEquals(setOf("Sandbox", "Production"), resolveAppleEnvironments("", prodProfile = false))
    }

    @Test
    fun `설정이 있으면 프로파일보다 설정이 우선`() {
        assertEquals(setOf("Sandbox"), resolveAppleEnvironments(" Sandbox , ", prodProfile = true))
    }

    // ── App Store 영수증 클레임 규칙(서명은 별개, 클레임만) ──

    private val bundle = "com.damoim.app"
    private val prodOnly = setOf(AppStoreJwsVerifier.ENV_PRODUCTION)
    private val withSandbox = setOf(AppStoreJwsVerifier.ENV_SANDBOX, AppStoreJwsVerifier.ENV_PRODUCTION)
    private val now: Instant = Instant.parse("2026-01-01T00:00:00Z")

    /** null을 주면 그 클레임이 아예 없는 영수증(= 검증을 건너뛰게 만드는 우회 시도). */
    private fun claims(
        environment: String? = AppStoreJwsVerifier.ENV_PRODUCTION,
        expiresAt: Instant? = null,
        bundleId: String? = bundle,
        productId: String? = std,
        transactionId: String? = "txn-1",
    ): JWTClaimsSet = JWTClaimsSet.Builder().apply {
        bundleId?.let { claim("bundleId", it) }
        environment?.let { claim("environment", it) }
        productId?.let { claim("productId", it) }
        transactionId?.let { claim("transactionId", it) }
        expiresAt?.let { claim("expiresDate", it.toEpochMilli()) }
    }.build()

    private fun read(c: JWTClaimsSet, allowed: Set<String> = prodOnly, at: Instant = now) =
        AppStoreJwsVerifier.readClaims(c, bundle, allowed, at)

    @Test
    fun `운영에서 샌드박스 영수증은 거부`() {
        assertFailsWith<AppStoreJwsVerifier.InvalidReceiptException> {
            read(claims(environment = AppStoreJwsVerifier.ENV_SANDBOX))
        }
    }

    @Test
    fun `environment 클레임이 없으면 거부`() {
        assertFailsWith<AppStoreJwsVerifier.InvalidReceiptException> { read(claims(environment = null)) }
    }

    @Test
    fun `허용 환경이 비어 있으면 거부(fail-closed)`() {
        assertFailsWith<AppStoreJwsVerifier.InvalidReceiptException> { read(claims(), allowed = emptySet()) }
    }

    @Test
    fun `개발에서는 샌드박스 영수증도 통과`() {
        val p = read(claims(environment = AppStoreJwsVerifier.ENV_SANDBOX), allowed = withSandbox)
        assertEquals(AppStoreJwsVerifier.ENV_SANDBOX, p.environment)
    }

    @Test
    fun `만료된 영수증은 거부`() {
        assertFailsWith<AppStoreJwsVerifier.InvalidReceiptException> {
            read(claims(expiresAt = now.minusSeconds(3_600)))
        }
    }

    @Test
    fun `만료 직후라도 클럭 스큐 안이면 통과`() {
        val p = read(claims(expiresAt = now.minusSeconds(60)))
        assertEquals(now.minusSeconds(60), p.expiresAt)
    }

    @Test
    fun `유효한 만료 시각은 payload로 전달된다`() {
        val expires = now.plusSeconds(30 * 86_400L)
        assertEquals(expires, read(claims(expiresAt = expires)).expiresAt)
    }

    @Test
    fun `만료 클레임이 없는 영수증은 통과(구독이 아닌 트랜잭션)`() {
        assertNull(read(claims(expiresAt = null)).expiresAt)
    }

    @Test
    fun `bundleId가 다르면 거부`() {
        assertFailsWith<AppStoreJwsVerifier.InvalidReceiptException> { read(claims(bundleId = "com.other.app")) }
    }
}
