package com.damoim.server.storage

/**
 * orphan 판정 순수 로직(테스트 용이). 어느 DB 행도 참조하지 않으면서(referencedKeys에 없음)
 * grace 기간보다 오래된(업로드 후 등록 대기가 아닌) 오브젝트만 삭제 대상으로 고른다.
 */
object OrphanSweepPlanner {
    fun plan(
        objects: List<StoredObject>,
        referencedKeys: Set<String>,
        nowMillis: Long,
        graceMillis: Long,
    ): List<String> =
        objects
            .filter { it.key !in referencedKeys && (nowMillis - it.lastModifiedEpochMillis) >= graceMillis }
            .map { it.key }
}
