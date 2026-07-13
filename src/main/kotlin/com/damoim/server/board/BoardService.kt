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
import com.damoim.server.domain.enums.MemberStatus
import com.damoim.server.domain.enums.RecruitStatus
import com.damoim.server.domain.repository.BoardPostRepository
import com.damoim.server.domain.repository.ClubMemberRepository
import com.damoim.server.domain.repository.CohortRepository
import com.damoim.server.domain.repository.CommentRepository
import com.damoim.server.domain.repository.PollOptionRepository
import com.damoim.server.domain.repository.PollRepository
import com.damoim.server.domain.repository.PostAttachmentRepository
import com.damoim.server.domain.repository.PostLikeRepository
import com.damoim.server.domain.repository.PostReadRepository
import com.damoim.server.domain.repository.RecentSearchRepository
import com.damoim.server.domain.repository.RecruitRepository
import com.damoim.server.domain.repository.UserRepository
import com.damoim.server.storage.StorageKeys
import com.damoim.server.storage.StorageService
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
    private val postLikeRepository: PostLikeRepository,
    private val postReadRepository: PostReadRepository,
    private val clubMemberRepository: ClubMemberRepository,
    private val cohortRepository: CohortRepository,
    private val userRepository: UserRepository,
    private val recentSearchRepository: RecentSearchRepository,
    private val membership: MembershipService,
    private val aggregates: BoardAggregates,
    private val storageService: StorageService,
) {
    // ── 조회 ──
    @Transactional(readOnly = true)
    fun home(userId: Long): BoardHomeResponse {
        val clubId = membership.currentMembership(userId).clubId
        val pinned = boardPostRepository.findPinned(clubId, PageRequest.of(0, PINNED_LIMIT))
        val feed = boardPostRepository.findRecentNonPinned(clubId, PageRequest.of(0, FEED_LIMIT))
        val ctx = summaryCtx(clubId, userId, pinned + feed)
        return BoardHomeResponse(pinned.map { toSummary(it, ctx) }, feed.map { toSummary(it, ctx) })
    }

    @Transactional(readOnly = true)
    fun list(userId: Long, category: String?): List<PostSummaryResponse> {
        val clubId = membership.currentMembership(userId).clubId
        val cat = category?.let { parseCategory(it) }
        val posts = boardPostRepository.findFeed(clubId, cat, PageRequest.of(0, LIST_LIMIT))
        val ctx = summaryCtx(clubId, userId, posts)
        return posts.map { toSummary(it, ctx) }
    }

    // ── 검색(40/85) ──
    @Transactional
    fun search(userId: Long, rawQuery: String): SearchResultResponse {
        val clubId = membership.currentMembership(userId).clubId
        val q = rawQuery.trim().take(100)
        if (q.isBlank()) return SearchResultResponse(q, emptyList())
        recentSearchRepository.touch(userId, q)             // 원문 기록(표시용)
        recentSearchRepository.prune(userId, RECENT_KEEP)   // 사용자당 상한 유지
        val esc = escapeLike(q)                             // LIKE 메타문자 이스케이프(와일드카드 주입 방지)
        val authorIds = userRepository.findIdsByNicknameContaining(esc).ifEmpty { listOf(-1L) }
        val posts = boardPostRepository.searchInClub(clubId, esc, authorIds, PageRequest.of(0, LIST_LIMIT))
        val ctx = summaryCtx(clubId, userId, posts)
        return SearchResultResponse(q, posts.map { toSummary(it, ctx) })
    }

    @Transactional(readOnly = true)
    fun searchSuggestions(userId: Long): SearchSuggestionsResponse {
        val recent = recentSearchRepository
            .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, RECENT_LIMIT)).map { it.query }
        return SearchSuggestionsResponse(recent, RECOMMENDED)
    }

    @Transactional
    fun removeRecentSearch(userId: Long, query: String) {
        recentSearchRepository.deleteByUserIdAndQuery(userId, query.trim())
    }

    @Transactional
    fun clearRecentSearches(userId: Long) {
        recentSearchRepository.deleteByUserId(userId)
    }

    /** 상세 — 최초 열람 시 조회 기록·조회수 증가(쓰기)라 readOnly 아님. */
    @Transactional
    fun detail(userId: Long, postId: Long): PostDetailResponse {
        val clubId = membership.currentMembership(userId).clubId
        val post = loadPostInClub(postId, clubId)
        val newRead = recordRead(post, userId)
        return mapDetail(userId, clubId, post, extraView = if (newRead) 1 else 0)
    }

    // ── 업로드(이미지/문서) ──
    /** 1단계 — 게시판 첨부 업로드 presigned PUT URL 발급. 활성 회원이면 누구나. */
    @Transactional(readOnly = true)
    fun createUploadUrl(userId: Long, req: BoardUploadUrlRequest): BoardUploadUrlResponse {
        val member = membership.currentMembership(userId)
        val cap = if (req.kind == "IMAGE") IMAGE_MAX_BYTES else DOC_MAX_BYTES
        if (req.sizeBytes > cap) throw BadRequestException("파일이 너무 큽니다.", "FILE_TOO_LARGE")
        val up = storageService.presignUpload(StorageKeys.forPost(member.clubId, req.fileName), req.contentType)
        return BoardUploadUrlResponse(up.url, up.key, up.expiresInSeconds)
    }

    // ── 쓰기(글) ──
    @Transactional
    fun create(userId: Long, req: CreatePostRequest): PostDetailResponse {
        val member = membership.currentMembership(userId)
        val category = parseCategory(req.category)
        if ((category == BoardCategory.NOTICE || req.pinned) && !canManageBoard(member)) {
            throw ForbiddenException("공지 작성·필독 지정은 운영진만 가능합니다.", "NOT_STAFF")
        }
        val post = boardPostRepository.save(
            BoardPost().apply {
                clubId = member.clubId
                this.category = category
                title = req.title.trim()
                content = req.content
                authorId = userId
                isPinned = req.pinned
            },
        )
        persistRichContent(member.clubId, post.id, req)
        return mapDetail(userId, member.clubId, post)
    }

    /** 첨부(이미지/문서/링크) 저장 — 생성·수정 공용. 이미지/문서는 storageKey 검증, 링크는 URL 검증. */
    private fun persistAttachments(clubId: Long, postId: Long, attachments: List<AttachmentInput>) {
        attachments.forEachIndexed { i, a ->
            val type = AttachmentType.valueOf(a.type)
            postAttachmentRepository.save(
                PostAttachment().apply {
                    this.postId = postId
                    this.type = type
                    position = i
                    when (type) {
                        AttachmentType.IMAGE -> {
                            // 실오브젝트 존재·소유권·상한 검증 후 키 저장. 라벨(캡션)은 선택.
                            storageKey = verifyMedia(clubId, a.storageKey, IMAGE_MAX_BYTES).key
                            imageLabel = a.imageLabel?.takeIf { it.isNotBlank() }
                        }
                        AttachmentType.FILE_DOC -> {
                            fileName = a.fileName?.takeIf { it.isNotBlank() }
                                ?: throw BadRequestException("파일명이 필요합니다.")
                            val vm = verifyMedia(clubId, a.storageKey, DOC_MAX_BYTES)
                            storageKey = vm.key
                            // 실크기(S3 HeadObject) 우선, 스텁 환경에선 클라 선언값 폴백.
                            sizeBytes = vm.sizeBytes ?: a.fileSizeBytes
                                ?: throw BadRequestException("파일 크기가 필요합니다.")
                        }
                        AttachmentType.LINK -> {
                            linkTitle = a.linkTitle?.takeIf { it.isNotBlank() }
                                ?: throw BadRequestException("링크 제목이 필요합니다.")
                            linkDomain = a.linkDomain?.takeIf { it.isNotBlank() }
                                ?: throw BadRequestException("링크 도메인이 필요합니다.")
                            linkUrl = a.linkUrl?.takeIf {
                                it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://"))
                            } ?: throw BadRequestException("링크 URL은 http(s):// 형식이어야 합니다.")
                        }
                    }
                },
            )
        }
    }

    private fun persistRichContent(clubId: Long, postId: Long, req: CreatePostRequest) {
        persistAttachments(clubId, postId, req.attachments)
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
                pollOptionRepository.save(PollOption().apply { pollId = poll.id; this.label = label; position = i })
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
        if (post.authorId != userId) throw ForbiddenException("본인 글만 수정할 수 있습니다.", "NOT_AUTHOR")
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
        // 첨부 전체 교체(수정 시 추가/삭제/순서 반영). 기존 이미지/문서는 클라가 storageKey로 재전송,
        // 새로 추가한 것만 실업로드된 키가 온다. 투표/모집은 별도(불변, 미변경).
        postAttachmentRepository.deleteByPostId(post.id)
        persistAttachments(member.clubId, post.id, req.attachments)
        return mapDetail(userId, member.clubId, post)
    }

    @Transactional
    fun delete(userId: Long, postId: Long) {
        val member = membership.currentMembership(userId)
        val post = loadPostInClub(postId, member.clubId)
        if (post.authorId != userId && !canManageBoard(member)) {
            throw ForbiddenException("삭제 권한이 없습니다.", "NOT_ALLOWED")
        }
        post.deletedAt = Instant.now()
        boardPostRepository.save(post)
    }

    @Transactional
    fun togglePin(userId: Long, postId: Long): Map<String, Boolean> {
        val member = membership.currentMembership(userId)
        if (!canManageBoard(member)) throw ForbiddenException("운영진만 가능합니다.", "NOT_STAFF")
        val post = loadPostInClub(postId, member.clubId)
        post.isPinned = !post.isPinned
        boardPostRepository.save(post)
        return mapOf("isPinned" to post.isPinned)
    }

    // ── 매핑/집계 ──
    private fun mapDetail(userId: Long, clubId: Long, post: BoardPost, extraView: Int = 0): PostDetailResponse {
        val authors = resolveAuthors(clubId, listOfNotNull(post.authorId))
        val authorInfo = post.authorId?.let { authors[it] } ?: DELETED_AUTHOR
        val comments = commentRepository.findByPost(post.id)
        val commentAuthorNames = userRepository.findAllById(comments.mapNotNull { it.authorId })
            .associate { it.id to it.nickname }
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
            viewCount = post.viewCount + extraView,   // 표시값만 가산(엔티티 미더티 → 원자증가 보존)
            likeCount = postLikeRepository.countByPostId(post.id).toInt(),
            likedByMe = postLikeRepository.existsByPostIdAndUserId(post.id, userId),
            commentCount = comments.size,
            isPinned = post.isPinned,
            isAuthorLeader = authorInfo.isLeader,
            isMine = post.authorId == userId,
            readRate = readRateFor(post, clubId),
            attachments = postAttachmentRepository.findByPostIdOrderByPosition(post.id).map { toAttachment(it) },
            poll = pollRepository.findByPostId(post.id)?.let { aggregates.pollResponse(it, userId) },
            recruit = recruitRepository.findByPostId(post.id)?.let { aggregates.recruitResponse(it, userId) },
            comments = comments.map { toComment(it, post.authorId, commentAuthorNames) },
        )
    }

    /** 최초 열람만 조회수 원자 증가(중복 열람 무시). 엔티티는 더티화하지 않는다(라이트백이 원자증가를 덮음). */
    private fun recordRead(post: BoardPost, userId: Long): Boolean {
        val newRead = postReadRepository.insertIfAbsent(post.id, userId) > 0
        if (newRead) boardPostRepository.incrementViewCount(post.id)
        return newRead
    }

    /** 필독 확인율(공지만) = 열람자 수 / 활동회원 수. */
    private fun readRateFor(post: BoardPost, clubId: Long): Int? {
        if (post.category != BoardCategory.NOTICE) return null
        val members = clubMemberRepository.countByClubIdAndStatus(clubId, MemberStatus.ACTIVE).toInt()
        if (members == 0) return 0
        val reads = postReadRepository.countByPostId(post.id).toInt()
        return (reads * 100 / members).coerceIn(0, 100)
    }

    private fun toAttachment(a: PostAttachment) = AttachmentResponse(
        type = a.type.name,
        imageUrl = a.storageKey?.takeIf { a.type == AttachmentType.IMAGE }?.let { storageService.presignView(it) },
        imageLabel = a.imageLabel,
        fileName = a.fileName,
        fileSize = a.sizeBytes?.let { SizeLabels.of(it) },
        fileUrl = a.storageKey?.takeIf { a.type == AttachmentType.FILE_DOC }
            ?.let { storageService.presignDownload(it, a.fileName ?: "file") },
        linkTitle = a.linkTitle,
        linkDomain = a.linkDomain,
        linkUrl = a.linkUrl,
        storageKey = a.storageKey,     // 수정 시 기존 이미지/문서 재참조용(presigned URL에도 포함됨)
    )

    private data class VerifiedMedia(val key: String, val sizeBytes: Long?)

    /** 첨부 storageKey 검증: 이 동아리 프리픽스(크로스테넌트 차단) + 실오브젝트 존재·상한(쿼터우회 차단). */
    private fun verifyMedia(clubId: Long, key: String?, capBytes: Long): VerifiedMedia {
        val k = key?.takeIf { it.isNotBlank() } ?: throw BadRequestException("업로드 키가 필요합니다.")
        if (!k.startsWith("posts/$clubId/")) throw ForbiddenException("잘못된 업로드 키입니다.", "INVALID_STORAGE_KEY")
        val size = if (storageService.verifiesSize) {
            storageService.objectSizeOrNull(k)
                ?: throw BadRequestException("업로드가 완료되지 않았습니다.", "UPLOAD_INCOMPLETE")
        } else {
            null
        }
        if (size != null && size > capBytes) throw BadRequestException("파일이 너무 큽니다.", "FILE_TOO_LARGE")
        return VerifiedMedia(k, size)
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

    private fun summaryCtx(clubId: Long, userId: Long, posts: List<BoardPost>): SummaryCtx {
        val ids = posts.map { it.id }
        return SummaryCtx(
            authors = resolveAuthors(clubId, posts.mapNotNull { it.authorId }),
            commentCounts = mapCounts(if (ids.isEmpty()) emptyList() else commentRepository.countByPosts(ids)),
            likeCounts = mapCounts(if (ids.isEmpty()) emptyList() else postLikeRepository.countByPosts(ids)),
            likedSet = if (ids.isEmpty()) emptySet() else postLikeRepository.likedPostIds(ids, userId).toSet(),
            thumbnails = if (ids.isEmpty()) emptyMap() else firstImageUrls(ids),
            readCounts = mapCounts(if (ids.isEmpty()) emptyList() else postReadRepository.countByPosts(ids)),
            memberCount = clubMemberRepository.countByClubIdAndStatus(clubId, MemberStatus.ACTIVE).toInt(),
        )
    }

    /** 각 게시글의 첫 이미지 첨부 → presigned view URL(목록 썸네일). */
    private fun firstImageUrls(postIds: List<Long>): Map<Long, String> {
        val firstKey = LinkedHashMap<Long, String>()
        postAttachmentRepository.findImageKeysByPosts(postIds).forEach { row ->
            firstKey.putIfAbsent(row[0] as Long, row[1] as String)
        }
        return firstKey.mapValues { storageService.presignView(it.value) }
    }

    private fun toSummary(p: BoardPost, ctx: SummaryCtx): PostSummaryResponse {
        val a = p.authorId?.let { ctx.authors[it] } ?: DELETED_AUTHOR
        val readRate = if (p.category == BoardCategory.NOTICE && ctx.memberCount > 0) {
            ((ctx.readCounts[p.id] ?: 0) * 100 / ctx.memberCount).coerceIn(0, 100)
        } else {
            null
        }
        return PostSummaryResponse(
            id = p.id,
            category = p.category.name,
            title = p.title,
            preview = preview(p.content),
            authorName = a.name,
            authorInitials = a.initials,
            authorGisu = a.gisu,
            timeLabel = p.createdAt?.let { TimeLabels.ago(it) } ?: "",
            likeCount = ctx.likeCounts[p.id] ?: 0,
            likedByMe = p.id in ctx.likedSet,
            commentCount = ctx.commentCounts[p.id] ?: 0,
            isPinned = p.isPinned,
            isAuthorLeader = a.isLeader,
            hasThumbnail = p.id in ctx.thumbnails,
            thumbnailUrl = ctx.thumbnails[p.id],
            readRate = readRate,
        )
    }

    // ── 내부 공용 ──
    private fun loadPostInClub(postId: Long, clubId: Long): BoardPost {
        val post = boardPostRepository.findByIdAndDeletedAtIsNull(postId)
            ?: throw NotFoundException("게시글을 찾을 수 없습니다.")
        if (post.clubId != clubId) throw NotFoundException("게시글을 찾을 수 없습니다.")
        return post
    }

    private fun canManageBoard(member: ClubMember) = member.memberRole != MemberRole.MEMBER

    private fun parseCategory(value: String): BoardCategory =
        runCatching { BoardCategory.valueOf(value) }.getOrElse { throw BadRequestException("카테고리가 올바르지 않습니다.") }

    private fun mapCounts(rows: List<Array<Any>>): Map<Long, Int> =
        rows.associate { (it[0] as Long) to (it[1] as Long).toInt() }

    private fun resolveAuthors(clubId: Long, authorIds: Collection<Long>): Map<Long, AuthorInfo> {
        val ids = authorIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        val names = userRepository.findAllById(ids).associate { it.id to it.nickname }
        val members = clubMemberRepository.findByClubIdAndUserIdIn(clubId, ids).associateBy { it.userId }
        val cohorts = cohortRepository.findAllById(members.values.mapNotNull { it.cohortId }.distinct())
            .associate { it.id to it.short }
        return ids.associateWith { id ->
            val name = names[id] ?: "탈퇴한 사용자"
            val m = members[id]
            AuthorInfo(name, initials(name), m?.cohortId?.let { cohorts[it] }, m?.memberRole == MemberRole.LEADER)
        }
    }

    private data class AuthorInfo(val name: String, val initials: String, val gisu: String?, val isLeader: Boolean)

    private class SummaryCtx(
        val authors: Map<Long, AuthorInfo>,
        val commentCounts: Map<Long, Int>,
        val likeCounts: Map<Long, Int>,
        val likedSet: Set<Long>,
        val thumbnails: Map<Long, String>,
        val readCounts: Map<Long, Int>,
        val memberCount: Int,
    )

    /** LIKE 메타문자 이스케이프('!' 이스케이프 문자 기준). '!' 먼저 이스케이프해야 이중처리 안 됨. */
    private fun escapeLike(s: String): String =
        s.replace("!", "!!").replace("%", "!%").replace("_", "!_")

    private fun preview(content: String): String =
        content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(80) ?: ""

    private fun initials(name: String): String = if (name.length <= 2) name else name.takeLast(2)

    private companion object {
        const val PINNED_LIMIT = 20
        const val FEED_LIMIT = 30
        const val LIST_LIMIT = 50
        const val RECENT_LIMIT = 10
        const val RECENT_KEEP = 20
        const val IMAGE_MAX_BYTES = 10L * 1024 * 1024  // 게시판 이미지 첨부 상한 10MB
        const val DOC_MAX_BYTES = 25L * 1024 * 1024     // 게시판 문서 첨부 상한 25MB
        val RECOMMENDED = listOf("모집", "공지", "일정", "회비")
        val DELETED_AUTHOR = AuthorInfo("탈퇴한 사용자", "탈퇴", null, false)
    }
}
