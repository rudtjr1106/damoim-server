package com.damoim.server.board

import com.damoim.server.common.TimeLabels
import com.damoim.server.domain.entity.Poll
import com.damoim.server.domain.entity.Recruit
import com.damoim.server.domain.repository.PollOptionRepository
import com.damoim.server.domain.repository.PollVoteRepository
import com.damoim.server.domain.repository.RecruitApplicationRepository
import com.damoim.server.domain.repository.UserRepository
import com.damoim.server.storage.StorageService
import org.springframework.stereotype.Component

/**
 * 투표·모집의 실집계 응답 빌더. 상세 조회(BoardService)와 상호작용(BoardInteractionService)이 공용.
 */
@Component
class BoardAggregates(
    private val pollOptionRepository: PollOptionRepository,
    private val pollVoteRepository: PollVoteRepository,
    private val recruitApplicationRepository: RecruitApplicationRepository,
    private val userRepository: UserRepository,
    private val storageService: StorageService,
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

    fun recruitResponse(recruit: Recruit, userId: Long, includeApplicants: Boolean = false): RecruitResponse {
        val current = recruitApplicationRepository.countByRecruitId(recruit.id).toInt()
        // 신청자 아바타 스택은 상세에서만(목록/홈 피드에서 켜면 카드마다 쿼리 → N+1).
        val applicants = if (!includeApplicants) {
            emptyList()
        } else {
            val apps = recruitApplicationRepository.findByRecruitIdOrderByCreatedAtAsc(recruit.id)
            val users = userRepository.findAllById(apps.map { it.userId }).associateBy { it.id }
            apps.mapNotNull { users[it.userId] }.map { u ->
                RecruitApplicantResponse(
                    name = u.nickname,
                    initials = if (u.nickname.length <= 2) u.nickname else u.nickname.takeLast(2),
                    imageUrl = u.profileImageKey?.let { storageService.presignView(it) } ?: u.profileImageUrl,
                )
            }
        }
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
            applicants = applicants,
        )
    }
}
