package com.damoim.server.domain.entity

import com.damoim.server.domain.enums.PermissionType
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "admin_permissions")
class AdminPermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0

    @Column(name = "admin_profile_id", nullable = false)
    var adminProfileId: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_type", nullable = false, length = 30)
    var permissionType: PermissionType = PermissionType.NOTICE_WRITE

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
}
