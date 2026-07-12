package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.BoardPost
import com.damoim.server.domain.enums.BoardCategory
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BoardPostRepository : JpaRepository<BoardPost, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): BoardPost?

    /** 조회수 원자적 증가(read-modify-write 경쟁 방지). */
    @Modifying
    @Query("update BoardPost p set p.viewCount = p.viewCount + 1 where p.id = :id")
    fun incrementViewCount(@Param("id") id: Long): Int

    /** 목록/피드 — 삭제 제외, 카테고리 선택(null이면 전체), 필독 먼저·최신순. 페이지 제한 필수(자원고갈 방지). */
    @Query(
        """
        select p from BoardPost p
        where p.clubId = :clubId and p.deletedAt is null
          and (:category is null or p.category = :category)
        order by p.isPinned desc, p.createdAt desc
        """,
    )
    fun findFeed(
        @Param("clubId") clubId: Long,
        @Param("category") category: BoardCategory?,
        pageable: Pageable,
    ): List<BoardPost>

    /** 홈 피드 — 필독 제외(필독은 배너로 별도 표시, 중복 방지)·최신순. */
    @Query(
        "select p from BoardPost p where p.clubId = :clubId and p.deletedAt is null and p.isPinned = false " +
            "order by p.createdAt desc",
    )
    fun findRecentNonPinned(@Param("clubId") clubId: Long, pageable: Pageable): List<BoardPost>

    /** 홈 상단 필독 배너용. */
    @Query(
        "select p from BoardPost p where p.clubId = :clubId and p.isPinned = true and p.deletedAt is null " +
            "order by p.createdAt desc",
    )
    fun findPinned(@Param("clubId") clubId: Long, pageable: Pageable): List<BoardPost>
}
