package com.damoim.server.common

import com.damoim.server.auth.KakaoUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import software.amazon.awssdk.core.exception.SdkException

/** 예외를 공통 봉투(ApiResponse) 실패 형태로 변환. 민감정보·스택은 노출하지 않는다. */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApi(e: ApiException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(e.status).body(ApiResponse.fail(e.code, e.message))

    /** Bean Validation 실패 — 필드 메시지만 노출. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val msg = e.bindingResult.fieldErrors.firstOrNull()?.let { "${it.field}: ${it.defaultMessage}" }
            ?: "입력값이 올바르지 않습니다."
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail("VALIDATION_ERROR", msg))
    }

    /** 요청 본문 파싱 실패(잘못된 JSON·필수 필드 누락) → 400. Jackson 내부는 노출하지 않음. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail("BAD_REQUEST", "요청 본문이 올바르지 않습니다."))

    /** 동시성 유니크 위반(중복 신청·승인 더블클릭 등) → 500 대신 409. 정상 운영 중 경쟁조건 흡수. */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(e: DataIntegrityViolationException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Data integrity violation: {}", e.mostSpecificCause.message)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.fail("CONFLICT", "이미 처리되었거나 중복된 요청입니다."))
    }

    @ExceptionHandler(KakaoUnavailableException::class)
    fun handleKakaoDown(e: KakaoUnavailableException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ApiResponse.fail("KAKAO_UNAVAILABLE", e.message ?: "외부 인증 서버 오류"))

    /** 스토리지(S3 등) 오류 → 502. 내부 상세는 로그로만. */
    @ExceptionHandler(SdkException::class)
    fun handleStorage(e: SdkException): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Storage/SDK error", e)
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.fail("STORAGE_ERROR", "스토리지 오류가 발생했습니다."))
    }

    /** 예상 못 한 예외 — 내부 메시지는 로그로만, 응답은 일반화. */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unhandled exception", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.fail("INTERNAL_ERROR", "서버 오류가 발생했습니다."))
    }
}
