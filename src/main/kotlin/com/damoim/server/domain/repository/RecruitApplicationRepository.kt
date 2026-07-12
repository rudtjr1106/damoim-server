package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.RecruitApplication
import org.springframework.data.jpa.repository.JpaRepository

interface RecruitApplicationRepository : JpaRepository<RecruitApplication, Long> {
    fun existsByRecruitIdAndUserId(recruitId: Long, userId: Long): Boolean
    fun countByRecruitId(recruitId: Long): Long
}
