package com.damoim.server.storage

import com.damoim.server.domain.repository.PostAttachmentRepository
import com.damoim.server.domain.repository.ResourceRepository
import com.damoim.server.domain.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * S3 orphan(어느 DB 행도 참조하지 않는 오브젝트) 정리 배치.
 *
 * 첨부 교체/삭제·프로필 사진 교체·등록되지 않은(presigned PUT 후 등록 API 미호출) 업로드가 남긴 오브젝트를
 * 주기적으로 회수한다. 참조 집합 = 살아있는 첨부/자료/프로필 키. 최근(grace 이내) 오브젝트는 등록 대기 중일 수
 * 있어 건너뛴다. 운영 S3에서만(app.storage.orphan-sweep.enabled=true) — 로컬 스텁엔 실 오브젝트가 없다.
 */
@Component
@ConditionalOnProperty(name = ["app.storage.orphan-sweep.enabled"], havingValue = "true")
class OrphanSweepService(
    private val storage: StorageService,
    private val props: StorageProperties,
    private val postAttachmentRepository: PostAttachmentRepository,
    private val resourceRepository: ResourceRepository,
    private val userRepository: UserRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.storage.orphan-sweep.cron}")
    fun sweep() {
        if (props.provider != "s3") {
            log.info("orphan 스윕 건너뜀(provider={} — 실 오브젝트 없음)", props.provider)
            return
        }
        val referenced = buildReferencedKeys()
        val graceMillis = props.orphanSweep.graceHours * 3_600_000L
        val now = Instant.now().toEpochMilli()
        var scanned = 0
        var deleted = 0
        MANAGED_PREFIXES.forEach { prefix ->
            val objects = storage.listObjects(prefix)
            scanned += objects.size
            OrphanSweepPlanner.plan(objects, referenced, now, graceMillis).forEach { key ->
                runCatching { storage.delete(key) }
                    .onSuccess { deleted++ }
                    .onFailure { log.warn("orphan 삭제 실패 key={}: {}", key, it.message) }
            }
        }
        log.info("orphan 스윕 완료: 스캔 {} · 참조 {} · 삭제 {}", scanned, referenced.size, deleted)
    }

    /** 살아있는 참조 키 전부(첨부 storageKey + 미삭제 자료 키 + 프로필 사진 키). */
    private fun buildReferencedKeys(): Set<String> = buildSet {
        addAll(postAttachmentRepository.findAllStorageKeys())
        addAll(resourceRepository.findLiveStorageKeys())
        addAll(userRepository.findAllProfileImageKeys())
    }

    private companion object {
        val MANAGED_PREFIXES = listOf("posts/", "resources/", "profiles/")
    }
}
