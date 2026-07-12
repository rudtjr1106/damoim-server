package com.damoim.server.board

import com.damoim.server.club.MembershipService
import com.damoim.server.common.BadRequestException
import com.damoim.server.common.ForbiddenException
import com.damoim.server.common.NotFoundException
import com.damoim.server.common.SizeLabels
import com.damoim.server.common.TimeLabels
import com.damoim.server.domain.entity.BoardPost
import com.damoim.server.domain.entity.ClubMember
import com.damoim.server.domain.entity.Comment
import com.damoim.server.domain.entity.Poll
import com.damoim.server.domain.entity.PollOption
import com.damoim.server.domain.entity.PostAttachment
import com.damoim.server.domain.entity.Recruit
import com.damoim.server.domain.enums.AttachmentType
import com.damoim.server.domain.enums.BoardCategory
import com.damoim.server.domain.enums.MemberRole
import com.damoim.server.domain.enums.RecruitStatus
import com.damoim.server.domain.repository.BoardPostRepository
import com.damoim.server.domain.repository.ClubMemberRepository
import com.damoim.server.domain.repository.CohortRepository
import com.damoim.server.domain.repository.CommentRepository
import com.damoim.server.domain.repository.PollOptionRepository
import com.damoim.server.domain.repository.PollRepository
import com.damoim.server.domain.repository.PostAttachmentRepository
import com.damoim.server.domain.repository.RecruitRepository
import com.damoim.server.domain.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class BoardService(
    private val boardPostRepository: BoardPostRepository,
    private val commentRepository: CommentRepository,
    private val postAttachmentRepository: PostAttachmentRepository,
    private val pollRepository: PollRepository,
    private val pollOptionRepository: PollOptionRepository,
    private val recruitRepository: RecruitRepository,
    private val clubMemberRepository: ClubMemberRepository,
    private val cohortRepository: CohortRepository,
    private val userRepository: UserRepository,
    private val membership: MembershipService,
) {
    // ── 조회 ──
    @Transactional(readOnly = true)
    fun home(userId: Long): BoardHomeResponse {
        val clubId = membership.currentMembership(userId).clubId
        val pinned = boardPostRepository.findPinned(clubId, PageRequest.of(0, PINNED_LIMIT))
        val feed = boardPostRepository.findRecentNonPinned(clubId, PageRequest.of(0, FEED_LIMIT))
        val authors = resolveAuthors(clubId, (pinned + feed).mapNotNull { it.authorId })
        val counts = commentCounts(pinned + feed)
        val thumbs = thumbnailPostIds(pinned + feed)
        return BoardHomeResponse(
            pinned = pinned.map { toSummary(it, authors, counts, thumbs) },
            feed = feed.map { toSummary(it, authors, counts, thumbs) },
        )
    }

    @Transactional(readOnly = true)
    fun list(userId: Long, category: String?): List<PostSummaryResponse> {
        val clubId = membership.currentMembership(userId).clubId
        val cat = category?.let { parseCategory(it) }
        val posts = boardPostRepository.findFeed(clubId, cat, PageRequest.of(0, LIST_LIMIT))
        val authors = resolveAuthors(clubId, posts.mapNotNull { it.authorId })
        val counts = commentCounts(posts)
        val thumbs = thumbnailPostIds(posts)
        return posts.map { toSummary(it, authors, counts, thumbs) }
    }

    @Transactional(readOnly = true)
    fun detail(userId: Long, postId: Long): PostDetailResponse {
        val clubId = membership.currentMembership(userId).clubId
        val post = loadPostInClub(postId, clubId)
        val authors = resolveAuthors(clubId, listOfNotNull(post.authorId))
        val comments = commentRepository.findByPost(postId)
        val commentAuthorNames = userRepository.findAllById(comments.mapNotNull { it.authorId })
            .associate { it.id to it.nickname }
        val authorInfo = post.authorId?.let { authors[it] } ?: DELETED_AUTHOR
        return PostDetailResponse(
            id = post.id,
            category = post.category.name,
            title = post.title,
            content = post.content,
            authorName = authorInfo.name,
            authorInitials = authorInfo.initials,
            authorGisu = authorInfo.gisu,
            timeLabel = post.createdAt?.let { TimeLabels.ago(it) } ?: "",
            dateLabel = post.createdAt?.let { TimeLabels.date(it) } ?: "",
            viewCount = post.viewCount,       // 조회수 증가는 C2
            likeCount = 0,                    // C2 post_likes 집계
            likedByMe = false,                // C2
            commentCount = comments.size,
            isPinned = post.isPinned,
            isAuthorLeader = authorInfo.isLeader,
            isMine = post.authorId == userId,
            attachments = postAttachmentRepository.findByPostIdOrderByPosition(postId).map { toAttachment(it) },
            poll = pollRepository.findByPostId(postId)?.let { toPoll(it) },
            recruit = recruitRepository.findByPostId(postId)?.let { toRecruit(it) },
            comments = comments.map { toComment(it, post.authorId, commentAuthorNames) },
        )
    }

    private fun toAttachment(a: PostAttachment) = AttachmentResponse(
        type = a.type.name,
        imageLabel = a.imageLabel,
        fileName = a.fileName,
        fileSize = a.sizeBytes?.let { SizeLabels.of(it) },
        linkTitle = a.linkTitle,
        linkDomain = a.linkDomain,
    )

    private fun toPoll(poll: Poll): PollResponse {
        val options = pollOptionRepository.findByPollIdOrderByPosition(poll.id)
        return PollResponse(
            anonymous = poll.anonymous,
            multiSelect = poll.multiSelect,
            deadlineLabel = poll.deadline?.let { TimeLabels.deadlineLabel(it) },
            dday = poll.deadline?.let { TimeLabels.dday(it) },
            totalVotes = 0,          // C2 poll_votes 집계
            myVotes = emptyList(),   // C2
            options = options.map { PollOptionResponse(it.position, it.label, 0, 0) },
        )
    }

    private fun toRecruit(r: Recruit): RecruitResponse {
        val current = 0             // C2 recruit_applications 집계
        return RecruitResponse(
            status = r.status.name,
            capacity = r.capacity,
            current = current,
            remaining = (r.capacity - current).coerceAtLeast(0),
            percent = if (r.capacity == 0) 0 else (current * 100 / r.capacity),
            deadlineLabel = r.deadline?.let { TimeLabels.deadlineLabel(it) },
            dday = r.deadline?.let { TimeLabels.dday(it) },
            method = r.method,
            appliedByMe = false,     // C2
        )
    }

    // ── 쓰기 ──
    @Transactional
    fun create(userId: Long, req: CreatePostRequest): PostDetailResponse {
        val member = membership.currentMembership(userId)
        val category = parseCategory(req.category)
        val pinned = req.pinned
        if ((category == BoardCategory.NOTICE || pinned) && !canManageBoard(member)) {
            throw ForbiddenException("공지 작성·필독 지정은 운영진만 가능합니다.", "NOT_STAFF")
        }
        val post = boardPostRepository.save(
            BoardPost().apply {
                clubId = member.clubId
                this.category = category
                title = req.title.trim()
                content = req.content
                authorId = userId
                isPinned = pinned
            },
        )
        persistRichContent(post.id, req)
        return detail(userId, post.id)
    }

    /** 첨부·투표·모집을 저장(작성 시). 편집은 텍스트만 — 리치 콘텐츠 편집은 후순위. */
    private fun persistRichContent(postId: Long, req: CreatePostRequest) {
        req.attachments.forEachIndexed { i, a ->
            val type = AttachmentType.valueOf(a.type)
            postAttachmentRepository.save(
                PostAttachment().apply {
                    this.postId = postId
                    this.type = type
                    position = i
                    when (type) {
                        AttachmentType.IMAGE ->
                            imageLabel = a.imageLabel?.takeIf { it.isNotBlank() }
                                ?: throw BadRequestException("이미지 라벨이 필요합니다.")
                        AttachmentType.FILE_DOC -> {
                            fileName = a.fileName?.takeIf { it.isNotBlank() }
                                ?: throw BadRequestException("파일명이 필요합니다.")
                            sizeBytes = a.fileSizeBytes ?: throw BadRequestException("파일 크기가 필요합니다.")
                        }
                        AttachmentType.LINK -> {
                            linkTitle = a.linkTitle?.takeIf { it.isNotBlank() }
                                ?: throw BadRequestException("링크 제목이 필요합니다.")
                            linkDomain = a.linkDomain?.takeIf { it.isNotBlank() }
                                ?: throw BadRequestException("링크 도메인이 필요합니다.")
                        }
                    }
                },
            )
        }
        req.poll?.let { p ->
            val opts = p.options.map { it.trim() }.filter { it.isNotEmpty() }
            if (opts.size < 2) throw BadRequestException("투표 항목은 2개 이상이어야 합니다.")
            if (opts.any { it.length > 200 }) throw BadRequestException("투표 항목은 200자 이하여야 합니다.")
            val poll = pollRepository.save(
                Poll().apply {
                    this.postId = postId
                    anonymous = p.anonymous
                    multiSelect = p.multiSelect
                    deadline = p.deadline
                },
            )
            opts.forEachIndexed { i, label ->
                pollOptionRepository.save(
                    PollOption().apply {
                        pollId = poll.id
                        this.label = label
                        position = i
                    },
                )
            }
        }
        req.recruit?.let { r ->
            recruitRepository.save(
                Recruit().apply {
                    this.postId = postId
                    status = RecruitStatus.OPEN
                    capacity = r.capacity
                    deadline = r.deadline
                    method = r.method?.takeIf { it.isNotBlank() }
                },
            )
        }
    }

    @Transactional
    fun update(userId: Long, postId: Long, req: UpdatePostRequest): PostDetailResponse {
        val member = membership.currentMembership(userId)
        val post = loadPostInClub(postId, member.clubId)
        if (post.authorId != userId) {
            throw ForbiddenException("본인 글만 수정할 수 있습니다.", "NOT_AUTHOR")
        }
        // 필독 글은 운영진만 수정(강등된 작성자의 배너 콘텐츠 변조 차단)
        if (post.isPinned && !canManageBoard(member)) {
            throw ForbiddenException("필독 글은 운영진만 수정할 수 있습니다.", "NOT_STAFF")
        }
        val category = parseCategory(req.category)
        if (category == BoardCategory.NOTICE && !canManageBoard(member)) {
            throw ForbiddenException("공지는 운영진만 작성할 수 있습니다.", "NOT_STAFF")
        }
        post.category = category
        post.title = req.title.trim()
        post.content = req.content
        boardPostRepository.save(post)
        return detail(userId, post.id)
    }

    @Transactional
    fun delete(userId: Long, postId: Long) {
        val member = membership.currentMembership(userId)
        val post = loadPostInClub(postId, member.clubId)
        // 작성자 본인 또는 운영진(모더레이션)만 삭제 가능
        if (post.authorId != userId && !canManageBoard(member)) {
            throw ForbiddenException("삭제 권한이 없습니다.", "NOT_ALLOWED")
        }
        post.deletedAt = Instant.now()
        boardPostRepository.save(post)
    }

    /** 필독 토글(운영진). */
    @Transactional
    fun togglePin(userId: Long, postId: Long): Map<String, Boolean> {
        val member = membership.currentMembership(userId)
        if (!canManageBoard(member)) throw ForbiddenException("운영진만 가능합니다.", "NOT_STAFF")
        val post = loadPostInClub(postId, member.clubId)
        post.isPinned = !post.isPinned
        boardPostRepository.save(post)
        return mapOf("isPinned" to post.isPinned)
    }

    // ── 내부 ──
    private fun loadPostInClub(postId: Long, clubId: Long): BoardPost {
        val post = boardPostRepository.findByIdAndDeletedAtIsNull(postId)
            ?: throw NotFoundException("게시글을 찾을 수 없습니다.")
        if (post.clubId != clubId) throw NotFoundException("게시글을 찾을 수 없습니다.") // 타 동아리 글 노출 차단
        return post
    }

    private fun canManageBoard(member: ClubMember) = member.memberRole != MemberRole.MEMBER

    private fun parseCategory(value: String): BoardCategory =
        runCatching { BoardCategory.valueOf(value) }
            .getOrElse { throw BadRequestException("카테고리가 올바르지 않습니다.") }

    private fun commentCounts(posts: List<BoardPost>): Map<Long, Int> {
        if (posts.isEmpty()) return emptyMap()
        return commentRepository.countByPosts(posts.map { it.id })
            .associate { (it[0] as Long) to (it[1] as Long).toInt() }
    }

    private fun toSummary(
        p: BoardPost,
        authors: Map<Long, AuthorInfo>,
        counts: Map<Long, Int>,
        thumbs: Set<Long>,
    ): PostSummaryResponse {
        val a = p.authorId?.let { authors[it] } ?: DELETED_AUTHOR
        return PostSummaryResponse(
            id = p.id,
            category = p.category.name,
            title = p.title,
            preview = preview(p.content),
            authorName = a.name,
            authorInitials = a.initials,
            authorGisu = a.gisu,
            timeLabel = p.createdAt?.let { TimeLabels.ago(it) } ?: "",
            likeCount = 0,
            commentCount = counts[p.id] ?: 0,
            isPinned = p.isPinned,
            isAuthorLeader = a.isLeader,
            hasThumbnail = p.id in thumbs,
            readRate = null,
        )
    }

    private fun thumbnailPostIds(posts: List<BoardPost>): Set<Long> {
        if (posts.isEmpty()) return emptySet()
        return postAttachmentRepository.findPostIdsWithType(posts.map { it.id }, AttachmentType.IMAGE).toSet()
    }

    private fun toComment(c: Comment, postAuthorId: Long?, names: Map<Long, String>): CommentResponse {
        val name = c.authorId?.let { names[it] } ?: "탈퇴한 사용자"
        return CommentResponse(
            id = c.id,
            authorName = name,
            authorInitials = initials(name),
            timeLabel = c.createdAt?.let { TimeLabels.ago(it) } ?: "",
            content = c.content,
            isReply = c.parentId != null,
            isAuthor = c.authorId != null && c.authorId == postAuthorId,
            parentId = c.parentId,
        )
    }

    private fun resolveAuthors(clubId: Long, authorIds: Collection<Long>): Map<Long, AuthorInfo> {
        val ids = authorIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        val names = userRepository.findAllById(ids).associate { it.id to it.nickname }
        val members = clubMemberRepository.findByClubIdAndUserIdIn(clubId, ids).associateBy { it.userId }
        val cohorts = cohortRepository
            .findAllById(members.values.mapNotNull { it.cohortId }.distinct())
            .associate { it.id to it.short }
        return ids.associateWith { id ->
            val name = names[id] ?: "탈퇴한 사용자"
            val m = members[id]
            AuthorInfo(
                name = name,
                initials = initials(name),
                gisu = m?.cohortId?.let { cohorts[it] },
                isLeader = m?.memberRole == MemberRole.LEADER,
            )
        }
    }

    private data class AuthorInfo(val name: String, val initials: String, val gisu: String?, val isLeader: Boolean)

    private fun preview(content: String): String =
        content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(80) ?: ""

    private fun initials(name: String): String = if (name.length <= 2) name else name.takeLast(2)

    private companion object {
        const val PINNED_LIMIT = 20
        const val FEED_LIMIT = 30
        const val LIST_LIMIT = 50
        val DELETED_AUTHOR = AuthorInfo("탈퇴한 사용자", "탈퇴", null, false)
    }
}
