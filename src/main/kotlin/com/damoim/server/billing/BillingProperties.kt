package com.damoim.server.billing

import com.damoim.server.domain.enums.PlanTier
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 인앱 결제 검증 설정. [verifyPurchases]=true면 구독 시 스토어 결제 증빙을 재검증(fail-closed) —
 * StoreKit/Play를 우회한 subscribe 직접 호출로 무료 구독되는 구멍을 닫는다. 개발은 false(현행 유지).
 * 상품 ID는 클라이언트/스토어 콘솔과 동일해야 한다.
 */
@ConfigurationProperties(prefix = "app.billing")
data class BillingProperties(
    val verifyPurchases: Boolean = false,
    val standardProductId: String = "com.damoim.app.subscription.standard",
    val proProductId: String = "com.damoim.app.subscription.pro",
    val apple: Apple = Apple(),
) {
    data class Apple(
        val bundleId: String = "",
        /** Apple 루트 CA 인증서(예: AppleRootCA-G3.cer) 경로. 미설정이면 App Store 검증 fail-closed. */
        val rootCertPath: String = "",
    )

    fun productIdFor(tier: PlanTier): String? = when (tier) {
        PlanTier.STANDARD -> standardProductId
        PlanTier.PRO -> proProductId
        else -> null
    }
}
