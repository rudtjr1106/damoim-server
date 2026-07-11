package com.damoim.server.common

import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

/**
 * 우리 컨트롤러(com.damoim.server)의 반환값을 공통 봉투 ApiResponse로 자동 래핑한다.
 * - 컨트롤러는 DTO만 반환 → {success:true, data:<dto>, error:null}
 * - 이미 ApiResponse면(에러 핸들러 등) 그대로 통과(이중 래핑 방지)
 * - basePackages 스코프라 actuator 등 프레임워크 응답은 건드리지 않음
 * - 문자열 컨버터는 제외(ClassCastException 방지)
 */
@RestControllerAdvice(basePackages = ["com.damoim.server"])
class ApiResponseWrapper : ResponseBodyAdvice<Any> {

    override fun supports(
        returnType: MethodParameter,
        converterType: Class<out HttpMessageConverter<*>>,
    ): Boolean = !StringHttpMessageConverter::class.java.isAssignableFrom(converterType)

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Any? = when (body) {
        is ApiResponse<*> -> body            // 이미 봉투 → 그대로
        is Unit -> ApiResponse.ok(null)      // 반환값 없는 핸들러 → data:null
        else -> ApiResponse.ok(body)
    }
}
