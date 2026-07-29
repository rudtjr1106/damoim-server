package com.damoim.server.report

import com.damoim.server.club.MembershipService
import com.damoim.server.common.BadRequestException
import com.damoim.server.common.ForbiddenException
import com.damoim.server.common.NotFoundException
import com.damoim.server.common.ConflictException
import com.damoim.server.common.TimeLabels
import com.damoim.server.domain.entity.BoardPost
import com.damoim.server.domain.entity.PostReport
import com.damoim.server.domain.entity.User
import com.damoim.server.domain.enums.MemberRole
import com.damoim.server.domain.enums.ReportAction
import com.damoim.server.domain.enums.ReportStatus
import com.damoim.server.domain.repository.BoardPostRepository
import com.damoim.server.domain.repository.CommentRepository
import com.damoim.server.domain.repository.PostReportRepository
import com.damoim.server.domain.repository.UserRepository
import com.damoim.server.storage.StorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 신고(82/34/35). 신고 접수는 활동 회원 누구나, '내 신고 내역'은 본인, '동아리 신고 목록/처리'는
 * 운영진(coarse 게이트 memberRole != MEMBER — 게시판 모더레이션과 동일)만 볼 수 있다.
 */
@Service
class ReportService(
    private val membership: MembershipService,
    private val postReportRepository: PostReportRepository,
    private val boardPostRepository: BoardPostRepository,
    private val commentRepository: CommentRepository,
    private val userRepository: UserRepository,
    private val storageService: StorageService,
) {
    /** 82 게시글/댓글 신고 접수 — 대상이 현재 동아리 콘텐츠인지 검증 후 저장. */
    @Transactional
    fun submit(userId: Long, req: SubmitReportRequest) {
        val targetType = req.targetType ?: throw BadRequestException("신고 대상 유형이 필요합니다.")
        val targetId = req.targetId ?: throw BadRequestException("신고 대상이 필요합니다.")
        val reason = req.reason ?: throw BadRequestException("신고 사유가 필요합니다.")
        val clubId = membership.currentMembership(userId).clubId

        val report = PostReport()
        report.reporterId = userId
        report.reason = reason
        report.detail = req.detail?.takeIf { it.isNotBlank() }
        when (targetType) {
            ReportTargetType.POST -> {
                val post = boardPostRepository.findById(targetId)
                    .orElseThrow { NotFoundException("게시글을 찾을 수 없습니다.") }
                if (post.clubId != clubId || post.deletedAt != null) throw NotFoundException("게시글을 찾을 수 없습니다.")
                report.postId = targetId
            }
            ReportTargetType.COMMENT -> {
                val comment = commentRepository.findById(targetId)
                    .orElseThrow { NotFoundException("댓글을 찾을 수 없습니다.") }
                if (comment.deletedAt != null) throw NotFoundException("댓글을 찾을 수 없습니다.")
                val post = boardPostRepository.findById(comment.postId)
                    .orElseThrow { NotFoundException("댓글을 찾을 수 없습니다.") }
                if (post.clubId != clubId) throw NotFoundException("댓글을 찾을 수 없습니다.")
                report.commentId = targetId
            }
        }
        postReportRepository.save(report)
    }

    /** 34 내가 신고한 내역(현재 동아리). */
    @Transactional(readOnly = true)
    fun listMine(userId: Long): List<MyReportResponse> {
        val clubId = membership.currentMembership(userId).clubId
        val reports = postReportRepository.findMineInClub(userId, clubId)
        if (reports.isEmpty()) return emptyList()
        val targets = resolveTargets(reports)
        val reportedIds = targets.values.mapNotNull { it.reportedUserId }.distinct()
        val users = userRepository.findAllById(reportedIds).associateBy { it.id }
        val names = membership.displayNamesFor(clubId, reportedIds)   // 44 동아리별 표시 이름
        return reports.map { r ->
            val t = targets.getValue(r.id)
            val reportedUser = t.reportedUserId?.let { users[it] }
            MyReportResponse(
                id = r.id,
                targetType = t.type,
                targetPreview = t.preview,
                reason = r.reason,
                status = r.status,
                reportedUserName = t.reportedUserId?.let { names[it] } ?: "탈퇴한 사용자",
                reportedUserImageUrl = reportedUser?.let { imageUrl(it) },
                createdLabel = r.createdAt?.let { TimeLabels.date(it) } ?: "",
            )
        }
    }

    /** 35 운영진 — 동아리 전체 신고 목록. */
    @Transactional(readOnly = true)
    fun listClubReports(userId: Long): List<ClubReportResponse> {
        val member = membership.currentMembership(userId)
        if (member.memberRole == MemberRole.MEMBER) throw ForbiddenException("운영진만 볼 수 있습니다.", "NO_PERMISSION")
        val reports = postReportRepository.findAllInClub(member.clubId)
        if (reports.isEmpty()) return emptyList()
        val targets = resolveTargets(reports)
        val userIds = (targets.values.mapNotNull { it.reportedUserId } + reports.map { it.reporterId }).distinct()
        val names = membership.displayNamesFor(member.clubId, userIds)   // 44 동아리별 표시 이름
        return reports.map { r ->
            val t = targets.getValue(r.id)
            ClubReportResponse(
                id = r.id,
                targetType = t.type,
                targetPreview = t.preview,
                reason = r.reason,
                status = r.status,
                action = r.action,
                reporterName = names[r.reporterId] ?: "탈퇴한 사용자",
                reportedUserName = t.reportedUserId?.let { names[it] } ?: "탈퇴한 사용자",
                createdLabel = r.createdAt?.let { TimeLabels.date(it) } ?: "",
            )
        }
    }

    /**
     * 35 운영진 — 신고 처리(대기 → 처리완료/기각). UGC 정책상 신고에는 반드시 조치 결과가 남아야 한다.
     * DELETE_CONTENT면 대상 글/댓글을 소프트 삭제하고, 같은 대상에 쌓인 나머지 대기 신고도 함께 종결한다.
     */
    @Transactional
    fun handle(userId: Long, reportId: Long, req: HandleReportRequest) {
        val member = membership.currentMembership(userId)
        if (member.memberRole == MemberRole.MEMBER) throw ForbiddenException("운영진만 처리할 수 있습니다.", "NO_PERMISSION")
        val decision = req.decision ?: throw BadRequestException("처리 결과가 필요합니다.")
        if (decision == ReportStatus.PENDING) throw BadRequestException("처리 결과가 올바르지 않습니다.")
        // 기각은 콘텐츠를 남기는 판단이므로 삭제 조치와 함께 올 수 없다(상태·조치 모순 방지).
        if (decision == ReportStatus.REJECTED && req.action != ReportAction.NONE) {
            throw BadRequestException("기각은 콘텐츠 조치를 함께 할 수 없습니다.")
        }
        val report = postReportRepository.findById(reportId)
            .orElseThrow { NotFoundException("신고를 찾을 수 없습니다.") }
        // 남의 동아리 신고 조작(IDOR) 차단 — post_reports엔 club_id가 없어 대상 글의 club_id로만 범위를 판정한다.
        val post = targetPostOf(report) ?: throw NotFoundException("신고를 찾을 수 없습니다.")
        if (post.clubId != member.clubId) throw NotFoundException("신고를 찾을 수 없습니다.")
        if (report.status != ReportStatus.PENDING) throw ConflictException("이미 처리된 신고입니다.", "ALREADY_HANDLED")

        if (req.action == ReportAction.DELETE_CONTENT) {
            deleteTarget(report, post)
            // 같은 콘텐츠에 여러 명이 신고했으면 한 번의 조치로 전부 종결(운영진 반복 처리 방지).
            siblingsOf(report).forEach { markHandled(it, userId, decision, req.action) }
        }
        markHandled(report, userId, decision, req.action)
    }

    /** 신고 대상이 속한 글(댓글 신고면 그 댓글의 글). 삭제된 글도 처리 대상이라 deletedAt은 보지 않는다. */
    private fun targetPostOf(report: PostReport): BoardPost? {
        val postId = report.postId
            ?: report.commentId?.let { commentRepository.findById(it).orElse(null)?.postId }
            ?: return null
        return boardPostRepository.findById(postId).orElse(null)
    }

    /** 신고된 콘텐츠 삭제 — 게시판과 동일하게 소프트 삭제(deletedAt). 이미 삭제됐으면 그대로 둔다. */
    private fun deleteTarget(report: PostReport, post: BoardPost) {
        val commentId = report.commentId
        if (commentId != null) {
            val comment = commentRepository.findById(commentId).orElse(null) ?: return
            if (comment.deletedAt == null) {
                comment.deletedAt = Instant.now()
                commentRepository.save(comment)
            }
            return
        }
        if (post.deletedAt == null) {
            post.deletedAt = Instant.now()
            boardPostRepository.save(post)
        }
    }

    /** 같은 대상(글 또는 댓글)의 다른 대기 신고들. */
    private fun siblingsOf(report: PostReport): List<PostReport> {
        val commentId = report.commentId
        val siblings = if (commentId != null) {
            postReportRepository.findByCommentIdAndStatus(commentId, ReportStatus.PENDING)
        } else {
            val postId = report.postId ?: return emptyList()
            postReportRepository.findByPostIdAndStatus(postId, ReportStatus.PENDING)
        }
        return siblings.filter { it.id != report.id }
    }

    private fun markHandled(report: PostReport, handlerId: Long, decision: ReportStatus, action: ReportAction) {
        report.status = decision
        report.action = action
        report.handledBy = handlerId
        report.handledAt = Instant.now()
        postReportRepository.save(report)
    }

    private data class TargetInfo(val type: ReportTargetType, val preview: String, val reportedUserId: Long?)

    /** 신고 목록의 대상(게시글 제목 / 댓글 일부)과 피신고자를 배치로 해석 — 신고 id → 대상 정보. */
    private fun resolveTargets(reports: List<PostReport>): Map<Long, TargetInfo> {
        val posts = boardPostRepository.findAllById(reports.mapNotNull { it.postId }.distinct()).associateBy { it.id }
        val comments = commentRepository.findAllById(reports.mapNotNull { it.commentId }.distinct()).associateBy { it.id }
        return reports.associate { r ->
            r.id to if (r.postId != null) {
                val p = posts[r.postId]
                TargetInfo(ReportTargetType.POST, p?.title ?: "삭제된 게시글", p?.authorId)
            } else {
                val c = comments[r.commentId]
                TargetInfo(ReportTargetType.COMMENT, c?.let { snippet(it.content) } ?: "삭제된 댓글", c?.authorId)
            }
        }
    }

    private fun snippet(text: String, max: Int = 40): String =
        text.replace("\n", " ").trim().let { if (it.length > max) it.take(max) + "…" else it }

    /** 피신고자 프로필 사진 — 내부 업로드 키가 있으면 presigned view, 없으면 외부(카카오) URL. */
    private fun imageUrl(u: User): String? =
        u.profileImageKey?.let { storageService.presignView(it) } ?: u.profileImageUrl
}
