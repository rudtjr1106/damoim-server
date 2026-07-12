package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.PlanFeature
import org.springframework.data.jpa.repository.JpaRepository

interface PlanFeatureRepository : JpaRepository<PlanFeature, Long> {
    fun findByPlanIdInOrderBySortOrderAsc(planIds: Collection<Long>): List<PlanFeature>
}
