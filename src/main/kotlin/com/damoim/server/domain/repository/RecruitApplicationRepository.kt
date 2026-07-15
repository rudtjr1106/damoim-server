package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.RecruitApplication
import org.springframework.data.jpa.repository.JpaRepository

interface RecruitApplicationRepository : JpaRepository<RecruitApplication, Long> {
    fun existsByRecruitIdAndUserId(recruitId: Long, userId: Long): Boolean
    fun countByRecruitId(recruitId: Long): Long

    /** 상세(84) 신청자 아바타 스택 — 신청순. */
    fun findByRecruitIdOrderByCreatedAtAsc(recruitId: Long): List<RecruitApplication>
}
