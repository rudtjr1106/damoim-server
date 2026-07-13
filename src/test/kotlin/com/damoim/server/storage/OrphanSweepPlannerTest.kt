package com.damoim.server.storage

import kotlin.test.Test
import kotlin.test.assertEquals

/** orphan 판정 순수 로직 검증(무DB·무컨텍스트). */
class OrphanSweepPlannerTest {

    private val hour = 3_600_000L
    private val now = 1_700_000_000_000L
    private val grace = 24 * hour

    @Test
    fun `미참조이면서 grace 초과한 것만 삭제 대상`() {
        val objects = listOf(
            StoredObject("posts/1/old-orphan.jpg", now - 48 * hour),   // 미참조·오래됨 → 삭제
            StoredObject("posts/1/kept.jpg", now - 48 * hour),         // 참조됨 → 유지
            StoredObject("posts/1/recent-orphan.jpg", now - 1 * hour), // 미참조지만 최근 → 유지(등록 대기 보호)
        )
        val plan = OrphanSweepPlanner.plan(objects, setOf("posts/1/kept.jpg"), now, grace)
        assertEquals(listOf("posts/1/old-orphan.jpg"), plan)
    }

    @Test
    fun `모두 참조되면 삭제 없음`() {
        val objects = listOf(
            StoredObject("a", now - 100 * hour),
            StoredObject("b", now - 100 * hour),
        )
        assertEquals(emptyList(), OrphanSweepPlanner.plan(objects, setOf("a", "b"), now, grace))
    }

    @Test
    fun `grace 경계값은 삭제(이상)`() {
        val objects = listOf(StoredObject("x", now - grace))  // 정확히 grace 만큼 오래됨 → 삭제
        assertEquals(listOf("x"), OrphanSweepPlanner.plan(objects, emptySet(), now, grace))
    }
}
