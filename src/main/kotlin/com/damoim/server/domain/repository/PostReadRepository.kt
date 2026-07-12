package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.PostRead
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PostReadRepository : JpaRepository<PostRead, Long> {
    fun countByPostId(postId: Long): Long

    /** 열람 기록을 원자적으로 삽입(중복이면 무시). 반환 1이면 최초 열람(조회수 증가 대상). */
    @Modifying
    @Query(
        value = "insert into post_reads(post_id, user_id, read_at) values (:postId, :userId, now()) " +
            "on conflict do nothing",
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("postId") postId: Long, @Param("userId") userId: Long): Int

    /** 글별 열람 수(필독 확인율 배치). 반환: [postId, count]. */
    @Query("select r.postId, count(r) from PostRead r where r.postId in :postIds group by r.postId")
    fun countByPosts(@Param("postIds") postIds: Collection<Long>): List<Array<Any>>
}
