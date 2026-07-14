package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.BoardPost
import com.damoim.server.domain.enums.BoardCategory
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface BoardPostRepository : JpaRepository<BoardPost, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): BoardPost?

    /** 회원 상세(18) 작성 글 수 — 삭제 제외. */
    fun countByClubIdAndAuthorIdAndDeletedAtIsNull(clubId: Long, authorId: Long): Long

    /** 회원 상세(18) 최근 활동 파생 — 마지막 작성 글 시각. */
    @Query(
        "select max(p.createdAt) from BoardPost p " +
            "where p.clubId = :clubId and p.authorId = :authorId and p.deletedAt is null",
    )
    fun latestPostAt(@Param("clubId") clubId: Long, @Param("authorId") authorId: Long): Instant?

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

    /** 홈(05/06) 게시판 미리보기 — 삭제 제외 최신순(필독 포함). */
    @Query(
        "select p from BoardPost p where p.clubId = :clubId and p.deletedAt is null " +
            "order by p.createdAt desc",
    )
    fun findRecent(@Param("clubId") clubId: Long, pageable: Pageable): List<BoardPost>

    /** 검색 — 제목/본문/작성자(authorIds) 일치. authorIds는 닉네임 매칭 결과(빈 리스트 금지, 서비스에서 -1 보정). */
    @Query(
        """
        select p from BoardPost p
        where p.clubId = :clubId and p.deletedAt is null
          and (lower(p.title) like lower(concat('%', :q, '%')) escape '!'
               or lower(p.content) like lower(concat('%', :q, '%')) escape '!'
               or p.authorId in :authorIds)
        order by p.createdAt desc
        """,
    )
    fun searchInClub(
        @Param("clubId") clubId: Long,
        @Param("q") q: String,
        @Param("authorIds") authorIds: Collection<Long>,
        pageable: Pageable,
    ): List<BoardPost>
}
