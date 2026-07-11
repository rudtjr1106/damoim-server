package com.damoim.server.common

/**
 * 전 API 공통 응답 봉투. 성공/실패가 같은 모양이라 클라는 ApiResponse<T> 하나로 파싱한다.
 * - 성공: success=true, data=<페이로드>, error=null
 * - 실패: success=false, data=null, error={code, message}
 *
 * code는 기계 판독용(클라 분기 기준) — 한글 message로 분기하지 말 것.
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: ApiErrorBody?,
) {
    companion object {
        fun <T> ok(data: T?): ApiResponse<T> = ApiResponse(true, data, null)
        fun fail(code: String, message: String): ApiResponse<Nothing> =
            ApiResponse(false, null, ApiErrorBody(code, message))
    }
}

data class ApiErrorBody(val code: String, val message: String)
