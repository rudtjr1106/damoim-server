package com.damoim.server.notification

import com.damoim.server.domain.repository.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationService(private val notificationRepository: NotificationRepository) {

    /** 내 알림 목록(37). 최신순. */
    @Transactional(readOnly = true)
    fun list(userId: Long): List<NotificationResponse> =
        notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).map(NotificationResponse::from)

    /** 모두 읽음 처리 → 벨 배지 해제. */
    @Transactional
    fun markAllRead(userId: Long) {
        notificationRepository.markAllRead(userId)
    }
}
