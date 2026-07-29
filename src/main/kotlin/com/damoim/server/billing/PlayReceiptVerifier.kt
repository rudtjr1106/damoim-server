package com.damoim.server.billing

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File

/** Play 결제 검증이 아직 구성되지 않음 — 호출부가 PLAY_NOT_CONFIGURED로 매핑(무검증 통과 금지). */
class PlayNotConfiguredException(message: String) : RuntimeException(message)

/** Google Play 결제 증빙 검증기 — 검증 결과 반환, 실패 시 예외. 테스트에서 대체 가능하도록 인터페이스. */
interface PlayReceiptVerifier {
    fun verify(productId: String, purchaseToken: String): VerifiedReceipt
}

/**
 * Play Developer API 검증기 자리. FirebaseConfig와 같은 "키가 설정돼야 켜진다" 조건부 구성이지만,
 * 실제 API 호출에는 google-api-services-androidpublisher 의존성이 필요해 **지금은 추가하지 않았다**
 * (Android 실결제 미출시 상태라 의존성만 늘어남). 어느 경우든 통과시키지 않으므로 현행 동작(거부)이 유지된다.
 *
 * 실검증을 붙일 때(서비스계정 키 준비 후):
 *  1) build.gradle.kts 에 의존성 추가
 *     implementation("com.google.apis:google-api-services-androidpublisher:v3-rev...")
 *  2) Play Console → 설정 → API 액세스에서 서비스계정 생성(재무 데이터 보기 권한) → JSON 키를
 *     secrets/play-service-account.json 에 두고 PLAY_SERVICE_ACCOUNT 경로 + PLAY_PACKAGE_NAME 주입.
 *  3) 아래 verify()를 구현: GoogleCredentials.fromStream(키) 로 AndroidPublisher 클라이언트를 만들고
 *     purchases().subscriptionsv2().get(packageName, purchaseToken) 호출 →
 *     subscriptionState == SUBSCRIPTION_STATE_ACTIVE 확인 후
 *     VerifiedReceipt(lineItems[0].productId, latestOrderId) 반환.
 *     (orderId가 트랜잭션 고유 ID = PurchaseLedger의 재사용 차단 키)
 */
@Component
class PlayDeveloperApiVerifier(private val props: BillingProperties) : PlayReceiptVerifier {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 패키지명 + 서비스계정 키 파일이 모두 있어야 '구성됨'(FirebaseConfig의 크리덴셜 필수 조건과 같은 기준). */
    private val configured: Boolean =
        props.play.packageName.isNotBlank() &&
            props.play.serviceAccountPath.takeIf { it.isNotBlank() }?.let { File(it).exists() } == true

    init {
        if (configured) {
            log.warn("Play 서비스계정 키가 설정됐지만 Play Developer API 클라이언트가 아직 연결되지 않았습니다 — PLAY 결제는 계속 거부됩니다.")
        }
    }

    override fun verify(productId: String, purchaseToken: String): VerifiedReceipt {
        if (!configured) {
            throw PlayNotConfiguredException("Play 결제 검증 설정(PLAY_PACKAGE_NAME/PLAY_SERVICE_ACCOUNT)이 없습니다.")
        }
        // 키는 있지만 실제 검증 호출이 없는 상태 — 여기서 통과시키면 무검증 구독이 되므로 거부(fail-closed).
        throw PlayNotConfiguredException("Play Developer API 클라이언트가 연결되지 않았습니다(위 주석 1~3단계 필요).")
    }
}
