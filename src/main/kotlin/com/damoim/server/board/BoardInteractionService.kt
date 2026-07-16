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
import com.damoim.server.domain.enums.NotificationTargetType
import com.damoim.server.domain.enums.NotificationType
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
import com.damoim.server.notification.NotifyUsersEvent
import org.springframework.context.ApplicationEventPublisher
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
    private val storageService: com.damoim.server.storage.StorageService,
    private val events: ApplicationEventPublisher,
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
        pollRepository.findByIdForUpdate(poll.id)   // 이 투표의 표를 직렬화(단일선택 1인1표·동시토글 원자화)
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
        pollRepository.findByIdForUpdate(poll.id)   // vote/clear 경쟁 직렬화
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
        // 정원 경쟁(TOCTOU) 차단 — 모집 행 락으로 count→검사→insert 직렬화
        recruitRepository.findByIdForUpdate(recruit.id)
        val current = recruitApplicationRepository.countByRecruitId(recruit.id).toInt()
        if (current >= recruit.capacity) {
            recruit.status = RecruitStatus.CLOSED
            recruitRepository.save(recruit)
            throw ConflictException("마감된 모집입니다.", "RECRUIT_CLOSED")
        }
        recruitApplicationRepository.save(RecruitApplication().apply { recruitId = recruit.id; this.userId = userId })
        if (current + 1 >= recruit.capacity) {   // 이 신청으로 정원 도달 → 자동 마감
            recruit.status = RecruitStatus.CLOSED
            recruitRepository.save(recruit)
        }
        return aggregates.recruitResponse(recruit, userId)
    }

    @Transactional
    fun addComment(userId: Long, postId: Long, req: AddCommentRequest): CommentResponse {
        val member = membership.currentMembership(userId)
        val post = loadPost(member, postId)
        var parentId = req.parentId
        var parent: Comment? = null
        if (parentId != null) {
            parent = commentRepository.findById(parentId).orElse(null)
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
        val u = userRepository.findById(userId).orElse(null)
        val name = u?.nickname ?: "나"
        val imageUrl = u?.profileImageKey?.let { storageService.presignView(it) } ?: u?.profileImageUrl
        // 알림 문구엔 응답용 "나" 폴백이 어색하므로 별도 표기
        publishCommentNotifications(member.clubId, userId, post, parent, u?.nickname ?: "회원", saved.content)
        return CommentResponse(
            id = saved.id,
            authorName = name,
            authorInitials = if (name.length <= 2) name else name.takeLast(2),
            authorImageUrl = imageUrl,
            timeLabel = "방금 전",
            content = saved.content,
            isReply = saved.parentId != null,
            isAuthor = post.authorId == userId,
            parentId = saved.parentId,
        )
    }

    /**
     * 댓글 알림 — 글 작성자에게 1건. 답글이면 답글 대상 댓글 작성자에게도 1건.
     * 본인 글에 본인 댓글/본인 댓글에 본인 답글은 생략, 글 작성자와 댓글 작성자가 같으면 1건만.
     * 수신자는 답글이 달린 **그 댓글**의 작성자(평탄화된 루트가 아님).
     */
    private fun publishCommentNotifications(
        clubId: Long,
        userId: Long,
        post: BoardPost,
        parent: Comment?,
        actorName: String,
        content: String,
    ) {
        val snippet = content.take(SNIPPET_CUT)
        val postAuthor = post.authorId?.takeIf { it != userId }
        if (postAuthor != null) {
            events.publishEvent(
                NotifyUsersEvent(
                    clubId, listOf(postAuthor), NotificationType.COMMENT,
                    NotificationTargetType.POST, post.id,
                    "${actorName}님이 회원님의 글에 댓글을 남겼어요: \"$snippet\"",
                ),
            )
        }
        // 답글 대상 댓글 작성자 — 본인·글 작성자(위에서 이미 발송)와 중복 제거
        val replyTo = parent?.authorId?.takeIf { it != userId && it != postAuthor }
        if (replyTo != null) {
            events.publishEvent(
                NotifyUsersEvent(
                    clubId, listOf(replyTo), NotificationType.COMMENT,
                    NotificationTargetType.POST, post.id,
                    "${actorName}님이 회원님의 댓글에 답글을 남겼어요: \"$snippet\"",
                ),
            )
        }
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

    private companion object {
        const val SNIPPET_CUT = 20   // 알림 문구에 넣을 댓글 내용 최대 길이
    }
}
