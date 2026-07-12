package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.Club
import org.springframework.data.jpa.repository.JpaRepository

interface ClubRepository : JpaRepository<Club, Long> {
    fun findByJoinCodeAndJoinCodeActiveIsTrue(joinCode: String): Club?
    fun existsByJoinCodeAndJoinCodeActiveIsTrue(joinCode: String): Boolean
}
