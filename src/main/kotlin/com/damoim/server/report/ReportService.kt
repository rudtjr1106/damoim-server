package com.damoim.server.report

import com.damoim.server.club.MembershipService
import com.damoim.server.common.BadRequestException
import com.damoim.server.common.ForbiddenException
import com.damoim.server.common.NotFoundException
import com.damoim.server.common.TimeLabels
import com.damoim.server.domain.entity.PostReport
import com.damoim.server.domain.entity.User
import com.damoim.server.domain.enums.MemberRole
import com.damoim.server.domain.repository.BoardPostRepository
import com.damoim.server.domain.repository.CommentRepository
import com.damoim.server.domain.repository.PostReportRepository
import com.damoim.server.domain.repository.UserRepository
import com.damoim.server.storage.StorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 신고(82/34/35). 신고 접수는 활동 회원 누구나, '내 신고 내역'은 본인, '동아리 신고 목록'은
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
        val users = userRepository.findAllById(targets.values.mapNotNull { it.reportedUserId }.distinct()).associateBy { it.id }
        return reports.map { r ->
            val t = targets.getValue(r.id)
            val reportedUser = t.reportedUserId?.let { users[it] }
            MyReportResponse(
                id = r.id,
                targetType = t.type,
                targetPreview = t.preview,
                reason = r.reason,
                reportedUserName = reportedUser?.nickname ?: "탈퇴한 사용자",
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
        val users = userRepository.findAllById(userIds).associateBy { it.id }
        return reports.map { r ->
            val t = targets.getValue(r.id)
            ClubReportResponse(
                id = r.id,
                targetType = t.type,
                targetPreview = t.preview,
                reason = r.reason,
                reporterName = users[r.reporterId]?.nickname ?: "탈퇴한 사용자",
                reportedUserName = t.reportedUserId?.let { users[it]?.nickname } ?: "탈퇴한 사용자",
                createdLabel = r.createdAt?.let { TimeLabels.date(it) } ?: "",
            )
        }
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
