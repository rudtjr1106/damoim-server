package com.damoim.server.settings

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// ── 응답 ──

/** 29 구독 관리 상태. priceLabel·nextBillingLabel은 서버 파생. memberUsed=활성 회원 수. */
data class SubscriptionStateResponse(
    val tier: String,                 // FREE / STANDARD / PRO
    val planName: String,             // "무료 플랜" / "스탠다드 플랜"
    val monthlyPriceLabel: String,    // "₩9,900" / "₩0"
    val nextBillingLabel: String,     // "2026.08.07" / "-"
    val memberUsed: Int,
    val memberLimit: Int,             // FREE=30, PRO=9999(무제한)
    val payments: List<PaymentRecordResponse>,
)

data class PaymentRecordResponse(
    val title: String,
    val dateLabel: String,            // "2026.07.12"
    val amountLabel: String,          // "₩9,900"
    val channel: String,              // "App Store"
)

/** 27 구독 플랜 카드. */
data class SubscriptionPlanResponse(
    val tier: String,
    val name: String,                 // "스탠다드"
    val priceKrw: Int,
    val priceLabel: String,           // "₩9,900"
    val memberLimitLabel: String,     // "회원 100명까지" / "회원 무제한"
    val features: List<PlanFeatureResponse>,
    val recommended: Boolean,
)

data class PlanFeatureResponse(val included: Boolean, val text: String)

// ── 요청 ──

/**
 * 27→인앱결제 성공 후 구독 활성화. 클라의 네이티브 결제(App Store/Google Play)가 성공하면 호출.
 * ⚠️ 현재 영수증(purchaseToken) 서버 검증 없음 — 출시 전 하드닝(스토어 서버 검증) 필요.
 */
data class SubscribeRequest(
    @field:NotBlank(message = "플랜은 필수입니다.")
    val tier: String,                 // STANDARD / PRO
    @field:Size(max = 30)
    val channel: String = "App Store",
    // 결제 증빙(서버 재검증용). verify-purchases=true면 필수.
    @field:Size(max = 20) val platform: String? = null,      // APP_STORE / PLAY
    @field:Size(max = 255) val productId: String? = null,
    @field:Size(max = 8192) val purchaseToken: String? = null, // iOS=JWS, Android=purchaseToken
)
