package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.*
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "club_members")
class ClubMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "club_id", nullable = false)
    var clubId: Long = 0

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 10)
    var memberRole: MemberRole = MemberRole.MEMBER

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    var status: MemberStatus = MemberStatus.ACTIVE

    @Column(name = "cohort_id", nullable = true)
    var cohortId: Long? = null

    @Column(name = "joined_at", nullable = false)
    var joinedAt: Instant = Instant.EPOCH

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}
