package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.PostReport
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 신고(82). post_reports에는 club_id가 없어 대상 게시글(board_posts.club_id) / 댓글의 게시글을
 * 조인해 동아리 범위로 조회한다. 게시글 XOR 댓글이 보장되므로 두 서브쿼리 중 하나만 매치된다.
 */
interface PostReportRepository : JpaRepository<PostReport, Long> {

    /** 34 내가 신고한 내역 — 현재 동아리 범위. */
    @Query(
        """
        SELECT r FROM PostReport r
        WHERE r.reporterId = :reporterId
          AND ( r.postId IN (SELECT p.id FROM BoardPost p WHERE p.clubId = :clubId)
             OR r.commentId IN (SELECT c.id FROM Comment c WHERE c.postId IN (SELECT p2.id FROM BoardPost p2 WHERE p2.clubId = :clubId)) )
        ORDER BY r.createdAt DESC
        """,
    )
    fun findMineInClub(@Param("reporterId") reporterId: Long, @Param("clubId") clubId: Long): List<PostReport>

    /** 35 운영진 — 동아리 전체 신고 목록. */
    @Query(
        """
        SELECT r FROM PostReport r
        WHERE r.postId IN (SELECT p.id FROM BoardPost p WHERE p.clubId = :clubId)
           OR r.commentId IN (SELECT c.id FROM Comment c WHERE c.postId IN (SELECT p2.id FROM BoardPost p2 WHERE p2.clubId = :clubId))
        ORDER BY r.createdAt DESC
        """,
    )
    fun findAllInClub(@Param("clubId") clubId: Long): List<PostReport>
}
