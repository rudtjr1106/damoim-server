package com.damoim.server.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI 3 / Swagger UI 설정. `/swagger-ui.html`(문서), `/v3/api-docs`(스펙 JSON).
 * 전 엔드포인트에 JWT Bearer 인증을 요구로 표기하고, 공통 응답 봉투 규격을 문서에 명시한다.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun damoimOpenApi(): OpenAPI {
        val scheme = "bearerAuth"
        return OpenAPI()
            .info(
                Info()
                    .title("다모임 API")
                    .version("v1")
                    .description(
                        """
                        다모임(동아리 커뮤니티 플랫폼) 백엔드 API.

                        **인증** — 카카오 로그인(`POST /api/auth/kakao`)으로 받은 JWT 액세스 토큰을
                        `Authorization: Bearer <token>`으로 전송한다. 우측 상단 **Authorize** 버튼에
                        토큰을 입력하면 모든 요청에 자동 적용된다.

                        **공통 응답 봉투** — 모든 응답은 다음 형태로 감싸진다:
                        `{ "success": true, "data": <아래 스키마>, "error": null }` (성공) /
                        `{ "success": false, "data": null, "error": { "code", "message" } }` (실패).
                        각 엔드포인트에 표기된 스키마는 **`data` 필드의 내용**이다.

                        **에러 코드** — 실패 시 `error.code`로 분기한다(예: `UNAUTHORIZED`, `FORBIDDEN`,
                        `NOT_FOUND`, `VALIDATION_ERROR`, `CONFLICT`, `NOT_LEADER`, `NO_ACTIVE_CLUB`).

                        **활성 동아리** — 대부분의 엔드포인트는 clubId를 받지 않고 JWT 주체의 활성 동아리에서
                        해석한다(전환은 `POST /api/clubs/switch`).
                        """.trimIndent(),
                    ),
            )
            .addSecurityItem(SecurityRequirement().addList(scheme))
            .components(
                Components().addSecuritySchemes(
                    scheme,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT 액세스 토큰(Bearer)"),
                ),
            )
    }
}
