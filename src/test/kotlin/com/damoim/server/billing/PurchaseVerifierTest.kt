package com.damoim.server.billing

import com.damoim.server.common.BadRequestException
import com.damoim.server.common.ForbiddenException
import com.damoim.server.domain.enums.PlanTier
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * 결제 증빙 게이팅(fail-closed) 검증 — 크립토 없이 순수 게이팅 로직만.
 * "verify-purchases=true면 유효한 증빙 없이는 구독 불가"라는 보안 속성이 성립함을 증명한다.
 */
class PurchaseVerifierTest {

    private val std = "com.damoim.app.subscription.standard"
    private val pro = "com.damoim.app.subscription.pro"
    private val props = BillingProperties(verifyPurchases = true, standardProductId = std, proProductId = pro)

    private fun okApple(product: String) = object : AppleReceiptVerifier {
        override fun verify(jws: String) = product
    }
    private fun failApple() = object : AppleReceiptVerifier {
        override fun verify(jws: String): String = throw AppStoreJwsVerifier.InvalidReceiptException("bad")
    }

    @Test
    fun `검증 비활성(dev)이면 증빙 없어도 통과`() {
        PurchaseVerifier(props.copy(verifyPurchases = false), failApple())
            .verify(null, null, null, PlanTier.STANDARD)  // 예외 없음
    }

    @Test
    fun `증빙 토큰이 없으면 거부`() {
        assertFailsWith<ForbiddenException> {
            PurchaseVerifier(props, okApple(std)).verify("APP_STORE", std, null, PlanTier.STANDARD)
        }
    }

    @Test
    fun `상품ID가 플랜과 다르면 거부`() {
        assertFailsWith<ForbiddenException> {
            PurchaseVerifier(props, okApple(std)).verify("APP_STORE", "wrong.product", "jws", PlanTier.STANDARD)
        }
    }

    @Test
    fun `App Store 서명 검증 실패면 거부`() {
        assertFailsWith<ForbiddenException> {
            PurchaseVerifier(props, failApple()).verify("APP_STORE", std, "jws", PlanTier.STANDARD)
        }
    }

    @Test
    fun `검증된 상품이 기대 플랜과 다르면 거부`() {
        assertFailsWith<ForbiddenException> {
            PurchaseVerifier(props, okApple(pro)).verify("APP_STORE", std, "jws", PlanTier.STANDARD)
        }
    }

    @Test
    fun `유효한 App Store 증빙이면 통과`() {
        PurchaseVerifier(props, okApple(std)).verify("APP_STORE", std, "jws", PlanTier.STANDARD)  // 예외 없음
    }

    @Test
    fun `Play는 아직 미구성이라 거부`() {
        assertFailsWith<ForbiddenException> {
            PurchaseVerifier(props, okApple(std)).verify("PLAY", std, "token", PlanTier.STANDARD)
        }
    }

    @Test
    fun `알 수 없는 플랫폼은 거부`() {
        assertFailsWith<ForbiddenException> {
            PurchaseVerifier(props, okApple(std)).verify("UNKNOWN", std, "token", PlanTier.STANDARD)
        }
    }

    @Test
    fun `상품ID 매핑 없는 플랜(FREE)은 거부`() {
        assertFailsWith<BadRequestException> {
            PurchaseVerifier(props, okApple(std)).verify("APP_STORE", std, "jws", PlanTier.FREE)
        }
    }
}
