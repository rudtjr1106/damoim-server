package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NotificationRepository : JpaRepository<Notification, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Notification>

    @Query("select count(n) from Notification n where n.userId = :userId and n.isRead = false")
    fun countUnread(@Param("userId") userId: Long): Long

    @Modifying
    @Query("update Notification n set n.isRead = true where n.userId = :userId and n.isRead = false")
    fun markAllRead(@Param("userId") userId: Long): Int
}
