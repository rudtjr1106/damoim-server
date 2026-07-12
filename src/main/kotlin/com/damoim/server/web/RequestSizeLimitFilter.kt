package com.damoim.server.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.filter.OncePerRequestFilter

/**
 * JSON 요청 본문 원시 크기 상한(DoS 방지). Content-Length가 상한을 넘으면 본문을 읽기 전에 413으로 차단한다.
 * chunked/Content-Length 위조는 서블릿 컨테이너 상한(server.tomcat.max-swallow-size)·게이트웨이의 몫.
 * 응답은 공통 봉투 {success,data,error}로 계약을 유지한다.
 */
class RequestSizeLimitFilter(private val maxBytes: Long) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val ct = request.contentType
        if (request.method in WRITE_METHODS &&
            ct != null && ct.startsWith("application/json") &&
            request.contentLengthLong > maxBytes
        ) {
            response.status = HttpStatus.PAYLOAD_TOO_LARGE.value()
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write(
                """{"success":false,"data":null,"error":{"code":"PAYLOAD_TOO_LARGE","message":"요청 본문이 너무 큽니다."}}""",
            )
            return
        }
        filterChain.doFilter(request, response)
    }

    private companion object {
        val WRITE_METHODS = setOf("POST", "PUT", "PATCH")
    }
}
