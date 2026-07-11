package com.damoim.server

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 오프라인/무DB 상태에서도 `./gradlew build`가 통과하도록 하는 경량 스모크 테스트.
 *
 * 전체 컨텍스트를 띄우는 @SpringBootTest는 실제 PostgreSQL(또는 Testcontainers)이 필요하므로,
 * 그 통합 테스트는 도메인 엔티티/리포지토리 구현 단계에서 Testcontainers와 함께 추가한다.
 */
class DamoimServerSmokeTest {

    @Test
    fun `프로젝트 뼈대가 컴파일되고 테스트 러너가 동작한다`() {
        assertTrue(true)
    }
}
