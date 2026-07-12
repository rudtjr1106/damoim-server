package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.PostLike
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PostLikeRepository : JpaRepository<PostLike, Long> {
    fun existsByPostIdAndUserId(postId: Long, userId: Long): Boolean
    fun countByPostId(postId: Long): Long
    fun deleteByPostIdAndUserId(postId: Long, userId: Long)

    @Query("select l.postId, count(l) from PostLike l where l.postId in :postIds group by l.postId")
    fun countByPosts(@Param("postIds") postIds: Collection<Long>): List<Array<Any>>

    /** 목록에서 현재 사용자가 좋아요한 postId들(배치). */
    @Query("select l.postId from PostLike l where l.postId in :postIds and l.userId = :userId")
    fun likedPostIds(@Param("postIds") postIds: Collection<Long>, @Param("userId") userId: Long): List<Long>
}
