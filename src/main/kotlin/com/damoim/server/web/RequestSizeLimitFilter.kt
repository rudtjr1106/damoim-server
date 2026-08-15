package com.damoim.server.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 요청 본문 원시 크기 상한(DoS 방지). Content-Length가 상한을 넘으면 본문을 읽기 전에 413으로 차단한다.
 *
 * 상한은 **경로별로 다르다**. JSON API는 [maxBytes](1MB)면 충분하지만, presigned 업로드
 * (`_localstorage` 경로의 PUT)는 사진·첨부 원본이 그대로 실려 오므로 [uploadMaxBytes]를 쓴다.
 * 전엔 `application/json`에만 상한을 걸어 업로드 경로가 **무제한**이었다(디스크 고갈).
 *
 * Content-Length는 클라가 정하는 값이고 chunked면 아예 없다(-1) → 이 필터는 조기 차단용 1차 방어일 뿐,
 * 실제 바이트 상한은 [com.damoim.server.storage.LocalStorageController]가 복사량을 세서 강제한다.
 * 응답은 공통 봉투 {success,data,error}로 계약을 유지한다.
 */
class RequestSizeLimitFilter(
    private val maxBytes: Long,
    private val uploadMaxBytes: Long,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val limit = limitFor(request)
        if (limit != null && request.contentLengthLong > limit) {
            writePayloadTooLarge(response)
            return
        }
        filterChain.doFilter(request, response)
    }

    /** 이 요청에 적용할 상한(상한 대상이 아니면 null). */
    private fun limitFor(request: HttpServletRequest): Long? {
        if (request.method !in WRITE_METHODS) return null
        // 업로드는 Content-Type으로 판정하지 않는다 — 클라가 실제 MIME(image/jpeg 등)을 그대로 보내므로
        // JSON 조건에 걸리지 않아 그동안 상한이 아예 없었다. 경로+메서드로 판정한다.
        if (request.method == "PUT" && request.requestURI.startsWith(LOCAL_STORAGE_PREFIX)) return uploadMaxBytes
        val contentType = request.contentType ?: return null
        return if (contentType.startsWith("application/json")) maxBytes else null
    }

    private companion object {
        val WRITE_METHODS = setOf("POST", "PUT", "PATCH")
        const val LOCAL_STORAGE_PREFIX = "/_localstorage/"
    }
}

/**
 * 413 응답을 공통 봉투로 직접 쓴다. 이 필터(본문 읽기 전)와 [com.damoim.server.storage.LocalStorageController]
 * (스트리밍 도중 초과 감지) 둘 다 @ExceptionHandler 바깥이라 응답을 손으로 만들어야 하는데,
 * 봉투 문자열이 두 벌로 갈라지면 클라의 code 분기가 조용히 어긋난다 → 한 곳에서만 만든다.
 */
internal fun writePayloadTooLarge(response: HttpServletResponse) {
    response.status = HttpStatus.PAYLOAD_TOO_LARGE.value()
    response.contentType = "application/json;charset=UTF-8"
    response.writer.write(
        """{"success":false,"data":null,"error":{"code":"PAYLOAD_TOO_LARGE","message":"요청 본문이 너무 큽니다."}}""",
    )
}
