package com.damoim.server.board

import com.damoim.server.common.TimeLabels
import com.damoim.server.domain.entity.Poll
import com.damoim.server.domain.entity.Recruit
import com.damoim.server.domain.repository.PollOptionRepository
import com.damoim.server.domain.repository.PollVoteRepository
import com.damoim.server.domain.repository.RecruitApplicationRepository
import org.springframework.stereotype.Component

/**
 * 투표·모집의 실집계 응답 빌더. 상세 조회(BoardService)와 상호작용(BoardInteractionService)이 공용.
 */
@Component
class BoardAggregates(
    private val pollOptionRepository: PollOptionRepository,
    private val pollVoteRepository: PollVoteRepository,
    private val recruitApplicationRepository: RecruitApplicationRepository,
) {
    fun pollResponse(poll: Poll, userId: Long): PollResponse {
        val options = pollOptionRepository.findByPollIdOrderByPosition(poll.id)
        val optionVotes = pollVoteRepository.countByOption(poll.id)
            .associate { (it[0] as Long) to (it[1] as Long).toInt() }
        val total = optionVotes.values.sum()
        val myOptionIds = pollVoteRepository.findByPollIdAndUserId(poll.id, userId).map { it.pollOptionId }.toSet()
        return PollResponse(
            anonymous = poll.anonymous,
            multiSelect = poll.multiSelect,
            deadlineLabel = poll.deadline?.let { TimeLabels.deadlineLabel(it) },
            dday = poll.deadline?.let { TimeLabels.dday(it) },
            totalVotes = total,
            myVotes = options.filter { it.id in myOptionIds }.map { it.position },
            options = options.map {
                val v = optionVotes[it.id] ?: 0
                PollOptionResponse(it.position, it.label, v, if (total == 0) 0 else v * 100 / total)
            },
        )
    }

    fun recruitResponse(recruit: Recruit, userId: Long): RecruitResponse {
        val current = recruitApplicationRepository.countByRecruitId(recruit.id).toInt()
        return RecruitResponse(
            status = recruit.status.name,
            capacity = recruit.capacity,
            current = current,
            remaining = (recruit.capacity - current).coerceAtLeast(0),
            percent = if (recruit.capacity == 0) 0 else (current * 100 / recruit.capacity).coerceIn(0, 100),
            deadlineLabel = recruit.deadline?.let { TimeLabels.deadlineLabel(it) },
            dday = recruit.deadline?.let { TimeLabels.dday(it) },
            method = recruit.method,
            appliedByMe = recruitApplicationRepository.existsByRecruitIdAndUserId(recruit.id, userId),
        )
    }
}
