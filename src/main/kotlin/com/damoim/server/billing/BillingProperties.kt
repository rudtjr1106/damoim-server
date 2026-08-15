package com.damoim.server.billing

import com.damoim.server.domain.enums.PlanTier
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 인앱 결제 검증 설정. [verifyPurchases]=true면 구독 시 스토어 결제 증빙을 재검증(fail-closed) —
 * StoreKit/Play를 우회한 subscribe 직접 호출로 무료 구독되는 구멍을 닫는다. 개발은 false(현행 유지),
 * 운영은 application-prod.yml이 기본 true로 올린다(꺼도 PurchaseVerifier가 구독을 거부).
 * 상품 ID는 클라이언트/스토어 콘솔과 동일해야 한다.
 */
@ConfigurationProperties(prefix = "app.billing")
data class BillingProperties(
    val verifyPurchases: Boolean = false,
    val standardProductId: String = "com.damoim.app.subscription.standard",
    val proProductId: String = "com.damoim.app.subscription.pro",
    val apple: Apple = Apple(),
    val play: Play = Play(),
) {
    data class Apple(
        val bundleId: String = "",
        /** Apple 루트 CA 인증서(예: AppleRootCA-G3.cer) 경로. 미설정이면 App Store 검증 fail-closed. */
        val rootCertPath: String = "",
        /**
         * 허용할 영수증 environment 클레임(쉼표 구분, 예: `Production` / `Sandbox,Production`).
         * 비우면 프로파일로 자동 결정 — 운영은 Production만, 개발/로컬은 Sandbox까지 허용.
         * 샌드박스 영수증도 Apple이 정식 서명하므로, 이 대조가 없으면 테스트 결제로 유료 플랜이 켜진다.
         */
        val environment: String = "",
    )

    /** Play Developer API 검증 자리(PlayDeveloperApiVerifier 주석 참고). 둘 다 채워져야 '구성됨'. */
    data class Play(
        /** Play Console 패키지명(예: com.damoim.app). */
        val packageName: String = "",
        /** Play Developer API 서비스계정 JSON 경로(커밋 금지). 미설정이면 PLAY 결제 fail-closed. */
        val serviceAccountPath: String = "",
    )

    fun productIdFor(tier: PlanTier): String? = when (tier) {
        PlanTier.STANDARD -> standardProductId
        PlanTier.PRO -> proProductId
        else -> null
    }
}
