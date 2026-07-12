package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.Comment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface CommentRepository : JpaRepository<Comment, Long> {

    @Query("select c from Comment c where c.postId = :postId and c.deletedAt is null order by c.createdAt asc")
    fun findByPost(@Param("postId") postId: Long): List<Comment>

    /** 회원 상세(18) 최근 활동 파생 — 마지막 댓글 시각(동아리 글에 단 댓글로 스코프). */
    @Query(
        "select max(c.createdAt) from Comment c, BoardPost p " +
            "where c.postId = p.id and p.clubId = :clubId " +
            "and c.authorId = :authorId and c.deletedAt is null and p.deletedAt is null",
    )
    fun latestCommentAt(@Param("clubId") clubId: Long, @Param("authorId") authorId: Long): Instant?

    @Query("select count(c) from Comment c where c.postId = :postId and c.deletedAt is null")
    fun countByPost(@Param("postId") postId: Long): Long

    /** 여러 글의 댓글 수 일괄 조회(N+1 방지). 반환: [postId, count] 쌍. */
    @Query(
        "select c.postId, count(c) from Comment c where c.postId in :postIds and c.deletedAt is null group by c.postId",
    )
    fun countByPosts(@Param("postIds") postIds: Collection<Long>): List<Array<Any>>
}
