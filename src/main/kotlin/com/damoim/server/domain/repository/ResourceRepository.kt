package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.Resource
import com.damoim.server.domain.enums.ResourceFolder
import com.damoim.server.domain.enums.ResourceVisibility
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ResourceRepository : JpaRepository<Resource, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): Resource?

    @Modifying
    @Query("update Resource r set r.downloadCount = r.downloadCount + 1 where r.id = :id")
    fun incrementDownload(@Param("id") id: Long): Int

    /**
     * 가시성 필터 목록. seeAll(운영진)이면 전체, 아니면 ALL_MEMBERS + 내 기수(cohortId)가 포함된 COHORT_ONLY만.
     * cohortId가 null이면(기수 미배정) COHORT_ONLY는 안 보임.
     */
    @Query(
        """
        select r from Resource r
        where r.clubId = :clubId and r.deletedAt is null
          and (:folder is null or r.folder = :folder)
          and (:seeAll = true or r.visibility = :allVis
               or exists (select 1 from ResourceCohort rc where rc.resourceId = r.id and rc.cohortId = :cohortId))
        order by r.createdAt desc
        """,
    )
    fun listVisible(
        @Param("clubId") clubId: Long,
        @Param("folder") folder: ResourceFolder?,
        @Param("seeAll") seeAll: Boolean,
        @Param("allVis") allVis: ResourceVisibility,
        @Param("cohortId") cohortId: Long?,
        pageable: Pageable,
    ): List<Resource>

    /** 동아리 저장공간 사용량(삭제 제외 SUM). */
    @Query("select coalesce(sum(r.sizeBytes), 0) from Resource r where r.clubId = :clubId and r.deletedAt is null")
    fun sumSizeBytes(@Param("clubId") clubId: Long): Long
}
