package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.*
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "recruits")
class Recruit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "post_id", nullable = false)
    var postId: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: RecruitStatus = RecruitStatus.OPEN

    @Column(name = "capacity", nullable = false)
    var capacity: Int = 0

    @Column(name = "deadline", nullable = true)
    var deadline: Instant? = null

    @Column(name = "method", nullable = true, length = 32)
    var method: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}
