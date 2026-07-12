package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.JoinApplication
import com.damoim.server.domain.enums.JoinStatus
import org.springframework.data.jpa.repository.JpaRepository

interface JoinApplicationRepository : JpaRepository<JoinApplication, Long> {
    fun findByClubIdAndStatusOrderByCreatedAtDesc(clubId: Long, status: JoinStatus): List<JoinApplication>
    fun findByClubIdAndStatusInOrderByCreatedAtDesc(
        clubId: Long,
        statuses: Collection<JoinStatus>,
    ): List<JoinApplication>
    fun findByClubIdAndUserIdAndStatus(clubId: Long, userId: Long, status: JoinStatus): JoinApplication?
}
