package com.damoim.server.notification

import com.damoim.server.domain.entity.DeviceToken
import com.damoim.server.domain.repository.DeviceTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 디바이스 토큰 등록/해제. 토큰은 전역 unique라 재로그인 시 소유자만 바뀐다. */
@Service
class DeviceTokenService(private val repository: DeviceTokenRepository) {

    @Transactional
    fun register(userId: Long, token: String, platform: String?) {
        val existing = repository.findByToken(token)
        if (existing != null) {
            existing.userId = userId
            if (platform != null) existing.platform = platform
        } else {
            repository.save(
                DeviceToken().apply {
                    this.userId = userId
                    this.token = token
                    this.platform = platform
                },
            )
        }
    }

    @Transactional
    fun unregister(token: String) {
        repository.deleteByToken(token)
    }
}
