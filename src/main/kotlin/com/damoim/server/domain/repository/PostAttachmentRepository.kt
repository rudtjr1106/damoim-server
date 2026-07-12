package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.PostAttachment
import com.damoim.server.domain.enums.AttachmentType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PostAttachmentRepository : JpaRepository<PostAttachment, Long> {
    fun findByPostIdOrderByPosition(postId: Long): List<PostAttachment>

    /** 목록 썸네일 여부 배치 — 이미지 첨부를 가진 postId들. */
    @Query("select distinct a.postId from PostAttachment a where a.postId in :postIds and a.type = :type")
    fun findPostIdsWithType(@Param("postIds") postIds: Collection<Long>, @Param("type") type: AttachmentType): List<Long>
}
