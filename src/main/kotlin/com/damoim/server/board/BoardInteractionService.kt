package com.damoim.server.board

import com.damoim.server.club.MembershipService
import com.damoim.server.common.BadRequestException
import com.damoim.server.common.ConflictException
import com.damoim.server.common.ForbiddenException
import com.damoim.server.common.NotFoundException
import com.damoim.server.domain.entity.BoardPost
import com.damoim.server.domain.entity.ClubMember
import com.damoim.server.domain.entity.Comment
import com.damoim.server.domain.entity.PollVote
import com.damoim.server.domain.entity.PostLike
import com.damoim.server.domain.entity.RecruitApplication
import com.damoim.server.domain.enums.MemberRole
import com.damoim.server.domain.enums.RecruitStatus
import com.damoim.server.domain.repository.BoardPostRepository
import com.damoim.server.domain.repository.CommentRepository
import com.damoim.server.domain.repository.PollOptionRepository
import com.damoim.server.domain.repository.PollRepository
import com.damoim.server.domain.repository.PollVoteRepository
import com.damoim.server.domain.repository.PostLikeRepository
import com.damoim.server.domain.repository.RecruitApplicationRepository
import com.damoim.server.domain.repository.RecruitRepository
import com.damoim.server.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 게시판 상호작용(쓰기): 좋아요·투표·모집신청·댓글. 모두 활성동아리 스코프. */
@Service
class BoardInteractionService(
    private val membership: MembershipService,
    private val boardPostRepository: BoardPostRepository,
    private val postLikeRepository: PostLikeRepository,
    private val pollRepository: PollRepository,
    private val pollOptionRepository: PollOptionRepository,
    private val pollVoteRepository: PollVoteRepository,
    private val recruitRepository: RecruitRepository,
    private val recruitApplicationRepository: RecruitApplicationRepository,
    private val commentRepository: CommentRepository,
    private val userRepository: UserRepository,
    private val aggregates: BoardAggregates,
) {
    @Transactional
    fun toggleLike(userId: Long, postId: Long): LikeResponse {
        val post = loadPost(membership.currentMembership(userId), postId)
        val liked = postLikeRepository.existsByPostIdAndUserId(post.id, userId)
        if (liked) {
            postLikeRepository.deleteByPostIdAndUserId(post.id, userId)
        } else {
            postLikeRepository.save(PostLike().apply { this.postId = post.id; this.userId = userId })
        }
        return LikeResponse(!liked, postLikeRepository.countByPostId(post.id).toInt())
    }

    @Transactional
    fun votePoll(userId: Long, postId: Long, optionIndex: Int): PollResponse {
        val post = loadPost(membership.currentMembership(userId), postId)
        val poll = pollRepository.findByPostId(post.id) ?: throw BadRequestException("투표가 없는 글입니다.")
        requireOpenPoll(poll)
        val option = pollOptionRepository.findByPollIdOrderByPosition(poll.id).getOrNull(optionIndex)
            ?: throw BadRequestException("옵션이 올바르지 않습니다.")
        if (poll.multiSelect) {
            // 복수 선택: 같은 옵션 재선택은 회수(토글)
            val existing = pollVoteRepository.findByPollOptionIdAndUserId(option.id, userId)
            if (existing != null) {
                pollVoteRepository.delete(existing)
            } else {
                pollVoteRepository.save(vote(poll.id, option.id, userId))
            }
        } else {
            // 단일 선택: 기존 표 교체
            pollVoteRepository.deleteByPollIdAndUserId(poll.id, userId)
            pollVoteRepository.save(vote(poll.id, option.id, userId))
        }
        return aggregates.pollResponse(poll, userId)
    }

    @Transactional
    fun clearPollVote(userId: Long, postId: Long): PollResponse {
        val post = loadPost(membership.currentMembership(userId), postId)
        val poll = pollRepository.findByPostId(post.id) ?: throw BadRequestException("투표가 없는 글입니다.")
        requireOpenPoll(poll)   // 마감된 투표는 회수도 불가(votePoll과 대칭)
        pollVoteRepository.deleteByPollIdAndUserId(poll.id, userId)
        return aggregates.pollResponse(poll, userId)
    }

    private fun requireOpenPoll(poll: com.damoim.server.domain.entity.Poll) {
        if (poll.deadline?.isBefore(Instant.now()) == true) {
            throw ConflictException("마감된 투표입니다.", "POLL_CLOSED")
        }
    }

    @Transactional
    fun applyRecruit(userId: Long, postId: Long): RecruitResponse {
        val post = loadPost(membership.currentMembership(userId), postId)
        val recruit = recruitRepository.findByPostId(post.id) ?: throw BadRequestException("모집이 없는 글입니다.")
        if (recruit.status == RecruitStatus.CLOSED) throw ConflictException("마감된 모집입니다.", "RECRUIT_CLOSED")
        if (recruit.deadline?.isBefore(Instant.now()) == true) {
            recruit.status = RecruitStatus.CLOSED
            recruitRepository.save(recruit)
            throw ConflictException("마감된 모집입니다.", "RECRUIT_CLOSED")
        }
        if (recruitApplicationRepository.existsByRecruitIdAndUserId(recruit.id, userId)) {
            throw ConflictException("이미 신청했습니다.", "ALREADY_APPLIED")
        }
        recruitApplicationRepository.save(RecruitApplication().apply { recruitId = recruit.id; this.userId = userId })
        // 정원 도달 시 자동 마감(경쟁 시 소폭 초과 가능 — 엄격 정원은 하드닝)
        if (recruitApplicationRepository.countByRecruitId(recruit.id).toInt() >= recruit.capacity) {
            recruit.status = RecruitStatus.CLOSED
            recruitRepository.save(recruit)
        }
        return aggregates.recruitResponse(recruit, userId)
    }

    @Transactional
    fun addComment(userId: Long, postId: Long, req: AddCommentRequest): CommentResponse {
        val post = loadPost(membership.currentMembership(userId), postId)
        var parentId = req.parentId
        if (parentId != null) {
            val parent = commentRepository.findById(parentId).orElse(null)
            if (parent == null || parent.postId != post.id || parent.deletedAt != null) {
                throw BadRequestException("원 댓글을 찾을 수 없습니다.")
            }
            parentId = parent.parentId ?: parent.id   // 답글의 답글은 최상위로 평탄화(1단계 유지)
        }
        val saved = commentRepository.save(
            Comment().apply {
                this.postId = post.id
                authorId = userId
                this.parentId = parentId
                content = req.content.trim()
            },
        )
        val name = userRepository.findById(userId).map { it.nickname }.orElse("나")
        return CommentResponse(
            id = saved.id,
            authorName = name,
            authorInitials = if (name.length <= 2) name else name.takeLast(2),
            timeLabel = "방금 전",
            content = saved.content,
            isReply = saved.parentId != null,
            isAuthor = post.authorId == userId,
            parentId = saved.parentId,
        )
    }

    @Transactional
    fun deleteComment(userId: Long, commentId: Long) {
        val member = membership.currentMembership(userId)
        val comment = commentRepository.findById(commentId).orElse(null)?.takeIf { it.deletedAt == null }
            ?: throw NotFoundException("댓글을 찾을 수 없습니다.")
        loadPost(member, comment.postId)   // 같은 동아리·글 존재 확인
        if (comment.authorId != userId && member.memberRole == MemberRole.MEMBER) {
            throw ForbiddenException("삭제 권한이 없습니다.", "NOT_ALLOWED")
        }
        comment.deletedAt = Instant.now()
        commentRepository.save(comment)
    }

    private fun loadPost(member: ClubMember, postId: Long): BoardPost {
        val post = boardPostRepository.findByIdAndDeletedAtIsNull(postId)
            ?: throw NotFoundException("게시글을 찾을 수 없습니다.")
        if (post.clubId != member.clubId) throw NotFoundException("게시글을 찾을 수 없습니다.")
        return post
    }

    private fun vote(pollId: Long, optionId: Long, userId: Long) =
        PollVote().apply { this.pollId = pollId; pollOptionId = optionId; this.userId = userId }
}
