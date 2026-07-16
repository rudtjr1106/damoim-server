package com.damoim.server.storage

import com.damoim.server.common.BadRequestException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets
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
        // form-urlencoded면 Spring의 FormContentFilter가 getParameter()용으로 바디를 먼저 다 읽어버려
        // inputStream이 비고 **0바이트 파일이 200과 함께 저장**된다(조용한 손실). presigned 업로드는
        // 폼 전송일 수 없으므로 거절한다. 정상 클라는 octet-stream이나 실제 MIME을 보낸다.
        if (request.contentType?.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE) == true) {
            throw BadRequestException("업로드 Content-Type이 올바르지 않습니다.", "INVALID_CONTENT_TYPE")
        }
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

    /**
     * 스토리지 키 = URI에서 프리픽스 뒤. requestURI는 **디코딩 안 된 원본**이라 반드시 퍼센트디코딩해야
     * 한다 — 안 하면 비ASCII(한글) 파일명이 "%ED%9A%8C..." 리터럴 이름으로 저장돼, 등록 단계의
     * objectSizeOrNull(원문 키)와 어긋나 UPLOAD_INCOMPLETE가 난다.
     * URLDecoder는 '+'를 공백으로 바꿔(form-urlencoded 규칙) 경로에 부적합하므로 UriUtils.decode를 쓴다.
     * PUT/GET 둘 다 이 함수를 쓰므로 저장·서빙이 항상 동일한 디코딩 키 기준으로 대칭이다.
     */
    private fun keyOf(request: HttpServletRequest): String {
        val raw = request.requestURI.substringAfter("/_localstorage/")
        val key = runCatching { UriUtils.decode(raw, StandardCharsets.UTF_8) }
            .getOrElse { throw BadRequestException("잘못된 스토리지 키입니다.", "INVALID_STORAGE_KEY") }
        if (!KEY.matches(key)) throw BadRequestException("잘못된 스토리지 키입니다.", "INVALID_STORAGE_KEY")
        return key
    }

    private companion object {
        /**
         * 허용 키 형태 화이트리스트 — [StorageKeys]가 만드는 형태만 통과시킨다.
         * ⚠️ [StorageKeys]에 새 프리픽스/키 형태를 추가하면 이 정규식도 함께 갱신할 것
         * (누락 시 로컬에서만 400이 나고 운영 S3는 조용히 통과해 동작이 괴리된다).
         *
         * 이 엔드포인트는 무인증(permitAll)이고 위에서 URI를 디코딩하므로, %2F('/')·%2E%2E('..')·
         * 역슬래시·%00(NUL)이 풀려도 여기서 먼저 걸러진다(LocalStorage.resolve의 normalize+startsWith는
         * 2차 방어로 남는다). 파일명이 "."·".."만인 경우도 배제해 디렉터리 대상 PUT을 막는다.
         * 파일명에 '가-힣'을 남겨둔 건 ASCII-only sanitize 이전에 만들어진 기존 한글 키를 계속
         * 읽을 수 있게 하기 위함이다(한글 자체는 경로 탈출 위험이 없다). '%'는 허용하지 않으므로
         * 인코딩↔디코딩 왕복이 무손실이다.
         */
        private val KEY = Regex("^(resources|posts|profiles|clubs)/\\d{1,19}/[0-9a-fA-F-]{36}/(?!\\.{1,2}$)[A-Za-z0-9._가-힣-]{1,120}$")
    }
}
