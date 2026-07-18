package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NotificationRepository : JpaRepository<Notification, Long> {
    // 활성 동아리 기준으로 스코프(동아리 무관 알림 clubId IS NULL은 어느 동아리에서나 보이도록 포함).
    @Query("select n from Notification n where n.userId = :userId and (n.clubId = :clubId or n.clubId is null) order by n.createdAt desc")
    fun findByUserIdAndClub(@Param("userId") userId: Long, @Param("clubId") clubId: Long): List<Notification>

    @Query("select count(n) from Notification n where n.userId = :userId and (n.clubId = :clubId or n.clubId is null) and n.isRead = false")
    fun countUnread(@Param("userId") userId: Long, @Param("clubId") clubId: Long): Long

    @Modifying
    @Query("update Notification n set n.isRead = true where n.userId = :userId and (n.clubId = :clubId or n.clubId is null) and n.isRead = false")
    fun markAllRead(@Param("userId") userId: Long, @Param("clubId") clubId: Long): Int
}
