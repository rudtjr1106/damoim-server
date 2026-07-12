package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.Subscription
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SubscriptionRepository : JpaRepository<Subscription, Long> {
    /** 동아리 1:1 구독(없으면 FREE 기본으로 파생). */
    fun findByClubId(clubId: Long): Subscription?

    /** 구독 변경 직렬화 — 재구독/동시 결제 경쟁의 lost-update·중복 방지(비관적 락). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.clubId = :clubId")
    fun findByClubIdForUpdate(@Param("clubId") clubId: Long): Subscription?
}
