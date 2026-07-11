package com.damoim.server.common

import com.damoim.server.auth.KakaoUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** 에러 응답 바디(민감정보·스택 없음). */
data class ApiError(val status: Int, val message: String)

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApi(e: ApiException): ResponseEntity<ApiError> =
        ResponseEntity.status(e.status).body(ApiError(e.status.value(), e.message))

    /** Bean Validation 실패 — 필드 메시지만 노출(내부 구조 노출 안 함). */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val msg = e.bindingResult.fieldErrors.firstOrNull()?.let { "${it.field}: ${it.defaultMessage}" }
            ?: "입력값이 올바르지 않습니다."
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError(400, msg))
    }

    /** 요청 본문 파싱 실패(잘못된 JSON·필수 필드 누락) → 400. Jackson 내부는 노출하지 않음. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(e: HttpMessageNotReadableException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError(400, "요청 본문이 올바르지 않습니다."))

    @ExceptionHandler(KakaoUnavailableException::class)
    fun handleKakaoDown(e: KakaoUnavailableException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiError(502, e.message ?: "외부 인증 서버 오류"))

    /** 예상 못 한 예외 — 내부 메시지는 로그로만, 응답은 일반화. */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiError> {
        log.error("Unhandled exception", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError(500, "서버 오류가 발생했습니다."))
    }
}
