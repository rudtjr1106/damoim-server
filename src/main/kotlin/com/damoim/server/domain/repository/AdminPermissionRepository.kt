package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.AdminPermission
import com.damoim.server.domain.enums.PermissionType
import org.springframework.data.jpa.repository.JpaRepository

interface AdminPermissionRepository : JpaRepository<AdminPermission, Long> {
    fun findByAdminProfileIdIn(adminProfileIds: Collection<Long>): List<AdminPermission>
    fun findByAdminProfileIdAndPermissionType(adminProfileId: Long, permissionType: PermissionType): AdminPermission?
}
