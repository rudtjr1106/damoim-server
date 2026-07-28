package com.damoim.server.notification

import com.damoim.server.domain.repository.DeviceTokenRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification as FcmNotification
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

/**
 * FCM 발송 래퍼. FirebaseMessaging 빈이 없으면(=app.firebase.enabled=false, 로컬/미설정) no-op.
 * 발송은 best-effort(예외 삼킴) — 알림 팬아웃의 부가 단계라 본 기능에 영향 없어야 한다.
 * UNREGISTERED/INVALID 응답 토큰은 device_tokens에서 정리한다.
 */
@Component
class PushSender(
    private val messagingProvider: ObjectProvider<FirebaseMessaging>,
    private val deviceTokenRepository: DeviceTokenRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun send(tokens: List<String>, title: String, body: String, data: Map<String, String>) {
        val messaging = messagingProvider.getIfAvailable() ?: return   // Firebase 비활성 → no-op
        if (tokens.isEmpty()) return
        runCatching {
            tokens.chunked(MULTICAST_LIMIT).forEach { chunk ->
                val message = MulticastMessage.builder()
                    .addAllTokens(chunk)
                    .setNotification(FcmNotification.builder().setTitle(title).setBody(body).build())
                    .putAllData(data)
                    .build()
                val response = messaging.sendEachForMulticast(message)
                if (response.failureCount > 0) {
                    val dead = response.responses.mapIndexedNotNull { i, r ->
                        when (r.exception?.messagingErrorCode) {
                            MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT -> chunk[i]
                            else -> null
                        }
                    }
                    if (dead.isNotEmpty()) deviceTokenRepository.deleteByTokenIn(dead)
                }
            }
        }.onFailure { log.warn("FCM 발송 실패(무시): {}", it.message) }
    }

    private companion object {
        const val MULTICAST_LIMIT = 500   // FCM sendEachForMulticast 토큰 상한
    }
}
