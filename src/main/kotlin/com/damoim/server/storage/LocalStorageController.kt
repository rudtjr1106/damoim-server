package com.damoim.server.storage

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files

/**
 * 로컬 개발용 스토리지 엔드포인트(provider=local 전용). presigned 흐름을 실제로 완성하기 위해
 * PUT=저장 / GET=서빙을 디스크에 구현한다(스텁이 아니라 실동작). 운영(S3)에선 로드되지 않는다.
 *
 * ⚠️ 클라가 presigned URL로 인증 없이 직접 호출하므로 [com.damoim.server.config.SecurityConfig]에서
 * `/_localstorage/` 이하는 permitAll 이어야 한다. 키에 대한 경로 탈출은 [LocalStorage]가 차단한다.
 */
@RestController
@ConditionalOnProperty(name = ["app.storage.provider"], havingValue = "local", matchIfMissing = true)
class LocalStorageController {

    /** 업로드 — presigned PUT. 바디 바이트를 키 경로에 저장한다. */
    @PutMapping("/_localstorage/**")
    fun put(request: HttpServletRequest, response: HttpServletResponse) {
        val target = LocalStorage.resolve(keyOf(request))
        Files.createDirectories(target.parent)
        request.inputStream.use { input -> Files.newOutputStream(target).use { input.copyTo(it) } }
        response.status = HttpStatus.OK.value()
    }

    /** 다운로드/인라인 뷰 — presigned GET. 저장된 바이트를 그대로 서빙(클라가 디코드). */
    @GetMapping("/_localstorage/**")
    fun get(request: HttpServletRequest, response: HttpServletResponse) {
        val target = LocalStorage.resolve(keyOf(request))
        if (!Files.exists(target)) {
            response.status = HttpStatus.NOT_FOUND.value()
            return
        }
        response.status = HttpStatus.OK.value()
        response.contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE
        Files.newInputStream(target).use { it.copyTo(response.outputStream) }
    }

    private fun keyOf(request: HttpServletRequest): String =
        request.requestURI.substringAfter("/_localstorage/")
}
