package com.damoim.server.common

import org.springframework.http.HttpStatus

/** 클라이언트에 안전하게 노출 가능한 메시지를 담는 API 예외. 내부 스택/원인은 응답에 싣지 않는다. */
sealed class ApiException(val status: HttpStatus, override val message: String) : RuntimeException(message)

class BadRequestException(message: String = "잘못된 요청입니다.") : ApiException(HttpStatus.BAD_REQUEST, message)
class UnauthorizedException(message: String = "인증이 필요합니다.") : ApiException(HttpStatus.UNAUTHORIZED, message)
class ForbiddenException(message: String = "권한이 없습니다.") : ApiException(HttpStatus.FORBIDDEN, message)
class NotFoundException(message: String = "찾을 수 없습니다.") : ApiException(HttpStatus.NOT_FOUND, message)
class ConflictException(message: String = "요청을 처리할 수 없습니다.") : ApiException(HttpStatus.CONFLICT, message)
