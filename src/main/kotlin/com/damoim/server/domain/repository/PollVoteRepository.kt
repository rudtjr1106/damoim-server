package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.PollVote
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PollVoteRepository : JpaRepository<PollVote, Long> {
    fun findByPollIdAndUserId(pollId: Long, userId: Long): List<PollVote>
    fun findByPollOptionIdAndUserId(pollOptionId: Long, userId: Long): PollVote?
    fun countByPollId(pollId: Long): Long
    fun deleteByPollIdAndUserId(pollId: Long, userId: Long)

    /** 옵션별 득표수. 반환: [pollOptionId, count]. */
    @Query("select v.pollOptionId, count(v) from PollVote v where v.pollId = :pollId group by v.pollOptionId")
    fun countByOption(@Param("pollId") pollId: Long): List<Array<Any>>
}
