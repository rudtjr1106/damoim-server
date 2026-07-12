package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.Cohort
import org.springframework.data.jpa.repository.JpaRepository

interface CohortRepository : JpaRepository<Cohort, Long> {
    fun findByClubIdOrderByCreatedAtAsc(clubId: Long): List<Cohort>
}
