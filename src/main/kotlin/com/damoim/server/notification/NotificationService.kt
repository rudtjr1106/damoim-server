package com.damoim.server.notification

import com.damoim.server.domain.repository.NotificationRepository
import com.damoim.server.club.MembershipService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val membership: MembershipService,
) {

    /** 내 알림 목록(37) — 활성 동아리 기준. 최신순. */
    @Transactional(readOnly = true)
    fun list(userId: Long): List<NotificationResponse> =
        notificationRepository.findByUserIdAndClub(userId, membership.currentClubId(userId))
            .map(NotificationResponse::from)

    /** 모두 읽음 처리(활성 동아리) → 벨 배지 해제. 다른 동아리 알림은 건드리지 않는다. */
    @Transactional
    fun markAllRead(userId: Long) {
        notificationRepository.markAllRead(userId, membership.currentClubId(userId))
    }

    /** 단건 읽음 처리(49) — 소유권으로 스코프. 남의 알림 id면 조용히 no-op. */
    @Transactional
    fun markRead(userId: Long, notificationId: Long) {
        notificationRepository.markReadById(notificationId, userId)
    }
}
