package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.ResourceCohort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ResourceCohortRepository : JpaRepository<ResourceCohort, Long> {
    @Query("select rc.cohortId from ResourceCohort rc where rc.resourceId = :resourceId")
    fun cohortIdsByResource(@Param("resourceId") resourceId: Long): List<Long>

    /** 목록 배치 — 반환: [resourceId, cohortId]. */
    @Query("select rc.resourceId, rc.cohortId from ResourceCohort rc where rc.resourceId in :ids")
    fun byResources(@Param("ids") ids: Collection<Long>): List<Array<Any>>

    fun deleteByResourceId(resourceId: Long)
}
