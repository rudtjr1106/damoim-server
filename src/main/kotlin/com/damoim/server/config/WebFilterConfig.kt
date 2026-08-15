package com.damoim.server.config

import com.damoim.server.storage.StorageProperties
import com.damoim.server.web.RequestSizeLimitFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

/** 서블릿 필터 등록. 바디 크기 상한 필터는 시큐리티·바디 파싱보다 먼저 실행되도록 최상위 우선순위. */
@Configuration
class WebFilterConfig {

    /** 업로드 상한은 스토리지 설정(app.storage.max-upload-bytes)을 그대로 쓴다 — 컨트롤러의 카운팅 상한과 같은 값이어야 한다. */
    @Bean
    fun requestSizeLimitFilter(
        @Value("\${app.request.max-body-bytes:1048576}") maxBytes: Long,
        storageProperties: StorageProperties,
    ): FilterRegistrationBean<RequestSizeLimitFilter> =
        FilterRegistrationBean(RequestSizeLimitFilter(maxBytes, storageProperties.maxUploadBytes)).apply {
            order = Ordered.HIGHEST_PRECEDENCE
            addUrlPatterns("/*")
        }
}
