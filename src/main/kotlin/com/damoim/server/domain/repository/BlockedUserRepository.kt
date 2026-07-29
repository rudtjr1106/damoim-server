package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.BlockedUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BlockedUserRepository : JpaRepository<BlockedUser, Long> {
    fun findByClubIdOrderByBlockedAtDesc(clubId: Long): List<BlockedUser>

    /** 중복 차단 멱등 처리 — 이미 있으면 기존 항목을 그대로 돌려준다(유니크 인덱스 위반 전에 차단). */
    fun findByClubIdAndBlockedUserId(clubId: Long, blockedUserId: Long): BlockedUser?

    /**
     * 차단 효과 반영용 — 동아리 차단 대상 userId만(엔티티 미로딩).
     * 조회 1회로 집합을 받아 메모리에서 필터한다(글/댓글마다 조회하면 N+1).
     */
    @Query("select b.blockedUserId from BlockedUser b where b.clubId = :clubId")
    fun findBlockedUserIds(@Param("clubId") clubId: Long): List<Long>
}
