package com.damoim.server.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

/**
 * 스캐폴딩 확인용 핑 엔드포인트. 서버가 실제로 떴는지 브라우저/curl로 바로 확인한다.
 * 도메인 엔드포인트(auth/club/board/...)는 다음 단계에서 이 옆에 추가된다.
 */
@RestController
@RequestMapping("/api")
class HealthController {

    @GetMapping("/ping")
    fun ping(): Map<String, Any> = mapOf(
        "service" to "damoim-server",
        "status" to "ok",
        "time" to OffsetDateTime.now().toString(),
    )
}
