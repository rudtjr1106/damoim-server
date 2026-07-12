package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.RecentSearch
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RecentSearchRepository : JpaRepository<RecentSearch, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): List<RecentSearch>
    fun deleteByUserIdAndQuery(userId: Long, query: String)
    fun deleteByUserId(userId: Long)

    /** 검색 시 최근 검색어 기록(있으면 시각 갱신). */
    @Modifying
    @Query(
        value = "insert into recent_searches(user_id, query, created_at) values (:userId, :query, now()) " +
            "on conflict (user_id, query) do update set created_at = now()",
        nativeQuery = true,
    )
    fun touch(@Param("userId") userId: Long, @Param("query") query: String): Int

    /** 사용자당 최신 keep건만 유지(무한 증가 방지). */
    @Modifying
    @Query(
        value = "delete from recent_searches where user_id = :userId and id not in " +
            "(select id from recent_searches where user_id = :userId order by created_at desc limit :keep)",
        nativeQuery = true,
    )
    fun prune(@Param("userId") userId: Long, @Param("keep") keep: Int): Int
}
