package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.Poll
import com.damoim.server.domain.entity.PollOption
import org.springframework.data.jpa.repository.JpaRepository

interface PollRepository : JpaRepository<Poll, Long> {
    fun findByPostId(postId: Long): Poll?
}

interface PollOptionRepository : JpaRepository<PollOption, Long> {
    fun findByPollIdOrderByPosition(pollId: Long): List<PollOption>
}
