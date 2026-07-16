package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.ClubMember
import com.damoim.server.domain.enums.MemberStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ClubMemberRepository : JpaRepository<ClubMember, Long> {
    fun findByClubIdAndUserId(clubId: Long, userId: Long): ClubMember?
    fun findByClubIdAndUserIdIn(clubId: Long, userIds: Collection<Long>): List<ClubMember>
    fun existsByClubIdAndUserId(clubId: Long, userId: Long): Boolean
    fun countByClubIdAndStatus(clubId: Long, status: MemberStatus): Long
    fun countByCohortIdAndStatus(cohortId: Long, status: MemberStatus): Long

    /** 명부 전체(활동+휴면) — 17 회원 목록. */
    fun findByClubId(clubId: Long): List<ClubMember>

    /** 특정 상태의 내 멤버십 전부 — 33 가입 동아리 목록·탈퇴 시 활성 동아리 재지정. */
    fun findByUserIdAndStatus(userId: Long, status: MemberStatus): List<ClubMember>

    /** 알림 팬아웃 수신자 — 활동 회원의 userId만(엔티티 미로딩). */
    @Query("select m.userId from ClubMember m where m.clubId = :clubId and m.status = :status")
    fun findUserIdsByClubIdAndStatus(
        @Param("clubId") clubId: Long,
        @Param("status") status: MemberStatus,
    ): List<Long>
}
