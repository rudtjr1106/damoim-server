package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.Poll
import com.damoim.server.domain.entity.PollOption
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PollRepository : JpaRepository<Poll, Long> {
    fun findByPostId(postId: Long): Poll?

    /** 단일선택 투표 원자화 — 한 투표(poll)의 삭제후삽입/토글을 비관적 락으로 직렬화(1인1표). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Poll p where p.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Poll?
}

interface PollOptionRepository : JpaRepository<PollOption, Long> {
    fun findByPollIdOrderByPosition(pollId: Long): List<PollOption>
    /** 투표 재조정(수정) 시 해당 투표의 모든 항목 제거. */
    fun deleteByPollId(pollId: Long)
}
