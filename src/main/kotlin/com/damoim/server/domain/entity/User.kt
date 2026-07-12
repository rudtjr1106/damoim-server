package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.*
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "users")
class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "nickname", nullable = false, length = 50)
    var nickname: String = ""

    @Column(name = "email", nullable = true, length = 255)
    var email: String? = null

    @Column(name = "profile_image_url", nullable = true, columnDefinition = "text")
    var profileImageUrl: String? = null

    /** 앱에서 올린 프로필 사진의 S3 키. 있으면 응답 URL은 presigned view로 파생(외부 카카오 URL은 위 필드). */
    @Column(name = "profile_image_key", nullable = true, columnDefinition = "text")
    var profileImageKey: String? = null

    @Column(name = "contact", nullable = true, length = 30)
    var contact: String? = null

    @Column(name = "active_club_id", nullable = true)
    var activeClubId: Long? = null

    @Column(name = "profile_completed_at", nullable = true)
    var profileCompletedAt: Instant? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
}
