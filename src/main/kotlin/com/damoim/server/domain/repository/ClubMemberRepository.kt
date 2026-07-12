package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.ClubMember
import com.damoim.server.domain.enums.MemberStatus
import org.springframework.data.jpa.repository.JpaRepository

interface ClubMemberRepository : JpaRepository<ClubMember, Long> {
    fun findByClubIdAndUserId(clubId: Long, userId: Long): ClubMember?
    fun findByClubIdAndUserIdIn(clubId: Long, userIds: Collection<Long>): List<ClubMember>
    fun existsByClubIdAndUserId(clubId: Long, userId: Long): Boolean
    fun countByClubIdAndStatus(clubId: Long, status: MemberStatus): Long
    fun countByCohortIdAndStatus(cohortId: Long, status: MemberStatus): Long
}
