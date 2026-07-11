package com.damoim.server.domain.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "payment_records")
class PaymentRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "subscription_id", nullable = false)
    var subscriptionId: Long = 0

    @Column(name = "title", nullable = false, length = 100)
    var title: String = ""

    @Column(name = "amount_krw", nullable = false)
    var amountKrw: Int = 0

    @Column(name = "channel", nullable = false, length = 30)
    var channel: String = ""

    @Column(name = "paid_at", nullable = false)
    var paidAt: Instant = Instant.EPOCH

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
