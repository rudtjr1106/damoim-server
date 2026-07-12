package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.BlockedUser
import org.springframework.data.jpa.repository.JpaRepository

interface BlockedUserRepository : JpaRepository<BlockedUser, Long> {
    fun findByClubIdOrderByBlockedAtDesc(clubId: Long): List<BlockedUser>
}
