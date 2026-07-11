package com.damoim.server.common

import org.springframework.http.HttpStatus

/**
 * 클라이언트에 안전하게 노출 가능한 메시지 + 기계 판독용 code를 담는 API 예외.
 * 내부 스택/원인은 응답에 싣지 않는다. code는 클라 분기 기준(한글 message로 분기 금지).
 */
sealed class ApiException(val status: HttpStatus, val code: String, override val message: String) :
    RuntimeException(message)

class BadRequestException(message: String = "잘못된 요청입니다.", code: String = "BAD_REQUEST") :
    ApiException(HttpStatus.BAD_REQUEST, code, message)

class UnauthorizedException(message: String = "인증이 필요합니다.", code: String = "UNAUTHORIZED") :
    ApiException(HttpStatus.UNAUTHORIZED, code, message)

class ForbiddenException(message: String = "권한이 없습니다.", code: String = "FORBIDDEN") :
    ApiException(HttpStatus.FORBIDDEN, code, message)

class NotFoundException(message: String = "찾을 수 없습니다.", code: String = "NOT_FOUND") :
    ApiException(HttpStatus.NOT_FOUND, code, message)

class ConflictException(message: String = "요청을 처리할 수 없습니다.", code: String = "CONFLICT") :
    ApiException(HttpStatus.CONFLICT, code, message)
