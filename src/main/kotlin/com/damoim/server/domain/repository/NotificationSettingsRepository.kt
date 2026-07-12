package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.NotificationSettings
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationSettingsRepository : JpaRepository<NotificationSettings, Long> {
    fun findByUserId(userId: Long): NotificationSettings?
}
