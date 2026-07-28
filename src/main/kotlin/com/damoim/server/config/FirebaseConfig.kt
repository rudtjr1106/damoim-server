package com.damoim.server.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream

/**
 * Firebase(FCM) 설정. `app.firebase.enabled=true`일 때만 FirebaseMessaging 빈을 만든다
 * (S3Config와 동일한 조건부 초기화 — 로컬/개발은 크리덴셜 없이 부팅되고 푸시는 no-op).
 * 크리덴셜(서비스계정 JSON)은 `FIREBASE_CREDENTIALS` 경로로 주입한다(커밋 금지).
 */
@ConfigurationProperties(prefix = "app.firebase")
data class FirebaseProperties(
    val enabled: Boolean = false,
    val credentialsPath: String = "",
    val projectId: String? = null,
)

@Configuration
@ConditionalOnProperty(name = ["app.firebase.enabled"], havingValue = "true")
class FirebaseConfig(private val props: FirebaseProperties) {

    @Bean
    fun firebaseMessaging(): FirebaseMessaging {
        require(props.credentialsPath.isNotBlank()) {
            "app.firebase.enabled=true이면 FIREBASE_CREDENTIALS(서비스계정 JSON 경로)가 필요합니다."
        }
        val credentials = FileInputStream(props.credentialsPath).use { GoogleCredentials.fromStream(it) }
        val builder = FirebaseOptions.builder().setCredentials(credentials)
        props.projectId?.takeIf { it.isNotBlank() }?.let { builder.setProjectId(it) }
        val app = if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(builder.build())
        } else {
            FirebaseApp.getInstance()
        }
        return FirebaseMessaging.getInstance(app)
    }
}
