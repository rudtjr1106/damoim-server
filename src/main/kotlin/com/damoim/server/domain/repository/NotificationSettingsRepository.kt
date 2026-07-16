package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.NotificationSettings
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationSettingsRepository : JpaRepository<NotificationSettings, Long> {
    fun findByUserId(userId: Long): NotificationSettings?

    /** 알림 팬아웃 시 수신자 설정 배치 조회(행이 없는 유저는 기본값=전부 켬). */
    fun findByUserIdIn(userIds: Collection<Long>): List<NotificationSettings>
}
