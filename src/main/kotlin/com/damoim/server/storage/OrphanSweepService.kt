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
 * orphan(어느 DB 행도 참조하지 않는 오브젝트) 정리 배치.
 *
 * 첨부 교체/삭제·프로필 사진 교체·등록되지 않은(presigned PUT 후 등록 API 미호출) 업로드가 남긴 오브젝트를
 * 주기적으로 회수한다. 참조 집합 = 살아있는 첨부/자료/프로필 키. 최근(grace 이내) 오브젝트는 등록 대기 중일 수
 * 있어 건너뛴다. 활성화는 app.storage.orphan-sweep.enabled=true 하나로만 판단한다 —
 * 전엔 provider != "s3"면 즉시 return이었는데, 자가호스팅 운영 기본값이 provider=local이라
 * (그쪽도 실제 디스크에 바이트가 쌓이는데) **회수 경로가 아예 없었다**.
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
        log.info(
            "orphan 스윕 완료(provider={}): 스캔 {} · 참조 {} · 삭제 {}",
            props.provider, scanned, referenced.size, deleted,
        )
    }

    /** 살아있는 참조 키 전부(첨부 storageKey + 미삭제 자료 키 + 프로필 사진 키). */
    private fun buildReferencedKeys(): Set<String> = buildSet {
        addAll(postAttachmentRepository.findAllStorageKeys())
        addAll(resourceRepository.findLiveStorageKeys())
        addAll(userRepository.findAllProfileImageKeys())
    }

    private companion object {
        /**
         * 스윕 대상 프리픽스 — [buildReferencedKeys]가 참조 집합을 만들어주는 프리픽스만 넣는다.
         * ⚠️ clubs/(동아리 대표 이미지)는 참조 키 쿼리가 없어 **일부러 제외**했다. 여기에 먼저 추가하면
         * 전부 미참조로 판정돼 동아리 로고가 통째로 삭제된다 — 추가하려면 참조 쿼리부터 만들 것.
         */
        val MANAGED_PREFIXES = listOf("posts/", "resources/", "profiles/")
    }
}
