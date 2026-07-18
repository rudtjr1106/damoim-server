package com.damoim.server.resource

import com.damoim.server.club.MembershipService
import com.damoim.server.common.BadRequestException
import com.damoim.server.common.ConflictException
import com.damoim.server.common.ForbiddenException
import com.damoim.server.common.NotFoundException
import com.damoim.server.common.SizeLabels
import com.damoim.server.common.TimeLabels
import com.damoim.server.domain.entity.ClubMember
import com.damoim.server.domain.entity.Resource
import com.damoim.server.domain.entity.ResourceCohort
import com.damoim.server.domain.enums.MemberRole
import com.damoim.server.domain.enums.ResourceFolder
import com.damoim.server.domain.enums.ResourceVisibility
import com.damoim.server.domain.repository.ClubMemberRepository
import com.damoim.server.domain.repository.ClubRepository
import com.damoim.server.domain.repository.ResourceCohortRepository
import com.damoim.server.domain.repository.ResourceRepository
import com.damoim.server.domain.repository.UserRepository
import com.damoim.server.storage.StorageKeys
import com.damoim.server.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ResourceService(
    private val membership: MembershipService,
    private val resourceRepository: ResourceRepository,
    private val resourceCohortRepository: ResourceCohortRepository,
    private val clubMemberRepository: ClubMemberRepository,
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val storageService: StorageService,
    private val subscriptionService: com.damoim.server.settings.SubscriptionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun list(userId: Long, folder: String?): List<ResourceResponse> {
        val member = membership.currentMembership(userId)
        val resources = resourceRepository.listVisible(
            member.clubId,
            folder?.let { parseFolder(it) },
            seeAll = canManage(member),
            allVis = ResourceVisibility.ALL_MEMBERS,
            cohortId = member.cohortId,
            pageable = PageRequest.of(0, LIST_LIMIT),
        )
        return mapList(userId, member.clubId, resources)
    }

    @Transactional(readOnly = true)
    fun detail(userId: Long, id: Long): ResourceResponse {
        val member = membership.currentMembership(userId)
        val r = loadInClub(id, member.clubId)
        if (!canView(member, r)) throw NotFoundException("자료를 찾을 수 없습니다.")
        return mapList(userId, member.clubId, listOf(r)).first()
    }

    @Transactional(readOnly = true)
    fun storage(userId: Long): StorageUsageResponse {
        val clubId = membership.currentMembership(userId).clubId
        val used = resourceRepository.sumSizeBytes(clubId)
        val quota = subscriptionService.effectiveLimits(clubId).storageQuotaBytes
        return StorageUsageResponse(
            usedBytes = used,
            usedLabel = SizeLabels.of(used),
            quotaBytes = quota,
            quotaLabel = SizeLabels.of(quota),
            percent = if (quota <= 0) 0 else ((used * 100 / quota).coerceIn(0, 100)).toInt(),
        )
    }

    /** 1단계 — 업로드 presigned URL 발급(권한·용량 검증). */
    @Transactional(readOnly = true)
    fun createUploadUrl(userId: Long, req: UploadUrlRequest): UploadUrlResponse {
        val member = membership.currentMembership(userId)
        val folder = parseFolder(req.folder)
        requireFolderPermission(member, folder)
        requireQuota(member.clubId, req.sizeBytes)
        val up = storageService.presignUpload(StorageKeys.forResource(member.clubId, req.fileName), req.contentType)
        return UploadUrlResponse(up.url, up.key, up.expiresInSeconds)
    }

    /** 2단계 — S3 업로드 완료 후 메타데이터 등록. */
    @Transactional
    fun create(userId: Long, req: CreateResourceRequest): ResourceResponse {
        val member = membership.currentMembership(userId)
        val folder = parseFolder(req.folder)
        requireFolderPermission(member, folder)
        // storageKey는 이 동아리에서 발급된 키만 허용(타 clubId 키·임의 오브젝트 참조 차단)
        if (!req.storageKey.startsWith("resources/${member.clubId}/")) {
            throw ForbiddenException("잘못된 업로드 키입니다.", "INVALID_STORAGE_KEY")
        }
        // 실제 업로드 크기로 쿼터 검증(클라 선언값 신뢰 금지). S3는 HeadObject, 로컬 스텁만 선언값 사용.
        val size = if (storageService.verifiesSize) {
            storageService.objectSizeOrNull(req.storageKey)
                ?: throw BadRequestException("업로드가 완료되지 않았습니다.", "UPLOAD_INCOMPLETE")
        } else {
            req.sizeBytes
        }
        // 쿼터 임계구역 진입 — 동아리 행 락으로 sum→검사→insert 직렬화(동시 업로드 초과 차단).
        // S3 HeadObject(네트워크) 이후에 락을 잡아 락 보유 시간을 최소화.
        clubRepository.findByIdForUpdate(member.clubId)
        requireQuota(member.clubId, size)
        val visibility = parseVisibility(req.visibility)
        val resource = resourceRepository.save(
            Resource().apply {
                clubId = member.clubId
                this.folder = folder
                title = req.title.trim()
                fileName = req.fileName
                ext = req.ext.uppercase().take(16)
                description = req.description
                sizeBytes = size
                uploaderId = userId
                this.visibility = visibility
                pageCount = req.pageCount
                storageUrl = req.storageKey
            },
        )
        if (visibility == ResourceVisibility.COHORT_ONLY) {
            val cohortIds = req.cohortIds.distinct()
            if (cohortIds.isEmpty()) throw BadRequestException("공개 대상 기수를 선택하세요.")
            cohortIds.forEach { cid ->
                resourceCohortRepository.save(ResourceCohort().apply { resourceId = resource.id; cohortId = cid })
            }
        }
        return mapList(userId, member.clubId, listOf(resource)).first()
    }

    @Transactional
    fun delete(userId: Long, id: Long) {
        val member = membership.currentMembership(userId)
        val r = loadInClub(id, member.clubId)
        if (r.uploaderId != userId && !canManage(member)) {
            throw ForbiddenException("삭제 권한이 없습니다.", "NOT_ALLOWED")
        }
        r.deletedAt = Instant.now()
        resourceRepository.save(r)
        r.storageUrl?.let { key ->
            runCatching { storageService.delete(key) }
                .onFailure { log.warn("스토리지 오브젝트 삭제 실패(orphan 가능) key={}: {}", key, it.message) }
        }
    }

    /** 다운로드 presigned URL 발급 + 다운로드 수 증가. */
    @Transactional
    fun createDownloadUrl(userId: Long, id: Long): DownloadUrlResponse {
        val member = membership.currentMembership(userId)
        val r = loadInClub(id, member.clubId)
        if (!canView(member, r)) throw NotFoundException("자료를 찾을 수 없습니다.")
        val key = r.storageUrl ?: throw NotFoundException("파일이 없습니다.")
        val url = storageService.presignDownload(key, r.fileName)
        resourceRepository.incrementDownload(r.id)
        return DownloadUrlResponse(url, r.fileName)
    }

    // ── 내부 ──
    private fun canManage(member: ClubMember) = member.memberRole != MemberRole.MEMBER

    private fun requireFolderPermission(member: ClubMember, folder: ResourceFolder) {
        if (!canManage(member) && folder != ResourceFolder.PHOTOS) {
            throw ForbiddenException("이 폴더에는 운영진만 올릴 수 있습니다.", "FOLDER_FORBIDDEN")
        }
    }

    private fun requireQuota(clubId: Long, addBytes: Long) {
        // 41 플랜별 저장 용량 집행 — 구독 티어(해지 만료 반영)에 따른 실효 쿼터.
        val quota = subscriptionService.effectiveLimits(clubId).storageQuotaBytes
        if (resourceRepository.sumSizeBytes(clubId) + addBytes > quota) {
            throw ConflictException("저장공간이 부족합니다.", "STORAGE_FULL")
        }
    }

    private fun canView(member: ClubMember, r: Resource): Boolean {
        if (canManage(member)) return true
        if (r.visibility == ResourceVisibility.ALL_MEMBERS) return true
        val cohortId = member.cohortId ?: return false
        return resourceCohortRepository.cohortIdsByResource(r.id).contains(cohortId)
    }

    private fun loadInClub(id: Long, clubId: Long): Resource {
        val r = resourceRepository.findByIdAndDeletedAtIsNull(id) ?: throw NotFoundException("자료를 찾을 수 없습니다.")
        if (r.clubId != clubId) throw NotFoundException("자료를 찾을 수 없습니다.")
        return r
    }

    private fun mapList(userId: Long, clubId: Long, resources: List<Resource>): List<ResourceResponse> {
        if (resources.isEmpty()) return emptyList()
        val uploaderIds = resources.mapNotNull { it.uploaderId }.distinct()
        val names = membership.displayNamesFor(clubId, uploaderIds)   // 44 동아리별 표시 이름
        val leaders = clubMemberRepository.findByClubIdAndUserIdIn(clubId, uploaderIds)
            .filter { it.memberRole == MemberRole.LEADER }.map { it.userId }.toSet()
        val cohortMap = resourceCohortRepository.byResources(resources.map { it.id })
            .groupBy({ it[0] as Long }, { it[1] as Long })
        return resources.map { r ->
            ResourceResponse(
                id = r.id,
                title = r.title,
                fileName = r.fileName,
                ext = r.ext,
                description = r.description,
                folder = r.folder.name,
                sizeLabel = SizeLabels.of(r.sizeBytes),
                sizeBytes = r.sizeBytes,
                uploaderName = r.uploaderId?.let { names[it] } ?: "탈퇴한 사용자",
                uploaderIsLeader = r.uploaderId in leaders,
                uploadedLabel = r.createdAt?.let { TimeLabels.ago(it) } ?: "",
                downloadCount = r.downloadCount,
                visibility = r.visibility.name,
                cohortIds = cohortMap[r.id] ?: emptyList(),
                pageCount = r.pageCount,
                isMine = r.uploaderId == userId,
            )
        }
    }

    private fun parseFolder(v: String): ResourceFolder =
        runCatching { ResourceFolder.valueOf(v) }.getOrElse { throw BadRequestException("폴더가 올바르지 않습니다.") }

    private fun parseVisibility(v: String): ResourceVisibility =
        runCatching { ResourceVisibility.valueOf(v) }.getOrElse { throw BadRequestException("공개 범위가 올바르지 않습니다.") }

    private companion object {
        const val LIST_LIMIT = 100
    }
}
