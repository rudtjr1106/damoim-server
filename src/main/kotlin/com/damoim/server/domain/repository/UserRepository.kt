package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long>
