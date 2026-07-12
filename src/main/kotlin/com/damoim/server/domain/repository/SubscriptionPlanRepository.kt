package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.SubscriptionPlan
import com.damoim.server.domain.enums.PlanTier
import org.springframework.data.jpa.repository.JpaRepository

interface SubscriptionPlanRepository : JpaRepository<SubscriptionPlan, Long> {
    fun findAllByOrderByPriceKrwAsc(): List<SubscriptionPlan>
    fun findByTier(tier: PlanTier): SubscriptionPlan?
}
