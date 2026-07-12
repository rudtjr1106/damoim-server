package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.AdminProfile
import org.springframework.data.jpa.repository.JpaRepository

interface AdminProfileRepository : JpaRepository<AdminProfile, Long> {
    fun findByClubMemberId(clubMemberId: Long): AdminProfile?
    fun findByClubMemberIdIn(clubMemberIds: Collection<Long>): List<AdminProfile>
}
