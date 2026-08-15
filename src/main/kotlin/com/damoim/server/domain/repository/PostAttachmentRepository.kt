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

    /** orphan 스윕 — 참조 중인 전 첨부 storageKey. */
    @Query("select a.storageKey from PostAttachment a where a.storageKey is not null")
    fun findAllStorageKeys(): List<String>

    /**
     * 동아리 게시판 첨부 사용량(bytes). 자료실 SUM과 합쳐 저장 쿼터에 집행한다
     * (예전엔 자료실만 세서 게시글 첨부로 쿼터를 우회할 수 있었다).
     * 삭제(소프트) 글의 첨부는 빼서 글을 지우면 용량이 회수되게 한다.
     * PostAttachment↔BoardPost는 연관 매핑이 없어 postId로 직접 조인한다.
     */
    @Query(
        "select coalesce(sum(a.sizeBytes), 0) from PostAttachment a, BoardPost p " +
            "where p.id = a.postId and p.clubId = :clubId and p.deletedAt is null",
    )
    fun sumSizeBytesByClub(@Param("clubId") clubId: Long): Long

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
