package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.Recruit
import org.springframework.data.jpa.repository.JpaRepository

interface RecruitRepository : JpaRepository<Recruit, Long> {
    fun findByPostId(postId: Long): Recruit?
}
