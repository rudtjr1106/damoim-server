package com.damoim.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

/**
 * FCM 디바이스 토큰 — 유저별 푸시 발송 대상. 같은 기기가 다른 계정으로 재로그인하면
 * (token은 전역 unique) 소유 user_id를 재할당한다. 유저 삭제 시 FK CASCADE로 정리.
 */
@Entity
@Table(name = "device_tokens")
class DeviceToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "token", nullable = false)
    var token: String = ""

    @Column(name = "platform", nullable = true)
    var platform: String? = null   // ANDROID | IOS

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}
