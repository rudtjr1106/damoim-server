package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.PostAttachment
import com.damoim.server.domain.enums.AttachmentType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PostAttachmentRepository : JpaRepository<PostAttachment, Long> {
    fun findByPostIdOrderByPosition(postId: Long): List<PostAttachment>

    /** 수정 시 첨부 전체 교체용 — 기존 첨부 삭제(벌크). */
    @Modifying
    @Query("delete from PostAttachment a where a.postId = :postId")
    fun deleteByPostId(@Param("postId") postId: Long)

    /** 목록 썸네일 여부 배치 — 이미지 첨부를 가진 postId들. */
    @Query("select distinct a.postId from PostAttachment a where a.postId in :postIds and a.type = :type")
    fun findPostIdsWithType(@Param("postIds") postIds: Collection<Long>, @Param("type") type: AttachmentType): List<Long>

    /**
     * 목록 썸네일 배치 — 각 postId의 이미지 첨부(postId, storageKey)를 position 순으로.
     * 서비스에서 postId별 첫 행만 취해 presigned view URL로 변환한다.
     */
    @Query(
        "select a.postId, a.storageKey from PostAttachment a " +
            "where a.postId in :postIds and a.type = com.damoim.server.domain.enums.AttachmentType.IMAGE " +
            "and a.storageKey is not null order by a.postId, a.position",
    )
    fun findImageKeysByPosts(@Param("postIds") postIds: Collection<Long>): List<Array<Any>>
}
