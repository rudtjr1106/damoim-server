package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.PaymentRecord
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentRecordRepository : JpaRepository<PaymentRecord, Long> {
    fun findBySubscriptionIdOrderByPaidAtDesc(subscriptionId: Long): List<PaymentRecord>
}
