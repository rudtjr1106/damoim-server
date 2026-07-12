package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.Cohort
import org.springframework.data.jpa.repository.JpaRepository

interface CohortRepository : JpaRepository<Cohort, Long> {
    fun findByClubIdOrderByCreatedAtAsc(clubId: Long): List<Cohort>

    /** 동아리 내 약칭 중복 검사(ux_cohorts_club_short 사전 확인). */
    fun existsByClubIdAndShort(clubId: Long, short: String): Boolean
    fun findByClubIdAndShort(clubId: Long, short: String): Cohort?
}
