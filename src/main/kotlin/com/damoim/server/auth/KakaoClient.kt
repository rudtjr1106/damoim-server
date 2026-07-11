package com.damoim.server.auth

import com.damoim.server.common.UnauthorizedException
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@ConfigurationProperties(prefix = "app.kakao")
data class KakaoProperties(val userInfoUrl: String)

/** 카카오 사용자 식별 결과(우리가 신뢰하는 값 — 카카오가 응답). */
data class KakaoUserInfo(
    val kakaoUserId: String,
    val nickname: String?,
    val email: String?,
    val profileImageUrl: String?,
)

/**
 * 클라가 보낸 카카오 access token을 **서버가 카카오 API로 재검증**한다.
 * 유효하지 않으면(401/403 등) UnauthorizedException. 클라의 신원 주장을 신뢰하지 않는다.
 */
@Component
class KakaoClient(props: KakaoProperties) {

    // 커넥트/리드 타임아웃 지정 — 카카오 지연 시 톰캣 워커 스레드 고갈 방지(비인증 엔드포인트 보호).
    private val restClient: RestClient = RestClient.builder()
        .baseUrl(props.userInfoUrl)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(2000)   // ms
                setReadTimeout(3000)      // ms
            },
        )
        .build()

    fun fetchUserInfo(kakaoAccessToken: String): KakaoUserInfo {
        val res = try {
            restClient.get()
                .uri("")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $kakaoAccessToken")
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ ->
                    throw UnauthorizedException("카카오 인증에 실패했습니다.", "KAKAO_AUTH_FAILED")
                }
                .body(KakaoMeResponse::class.java)
        } catch (e: UnauthorizedException) {
            throw e
        } catch (e: Exception) {
            // 네트워크/5xx 등은 상위에서 502로 처리하도록 런타임 예외 전파(민감정보 미포함 메시지)
            throw KakaoUnavailableException("카카오 인증 서버에 연결할 수 없습니다.")
        }
        val id = res?.id ?: throw UnauthorizedException("카카오 인증에 실패했습니다.", "KAKAO_AUTH_FAILED")
        return KakaoUserInfo(
            kakaoUserId = id.toString(),
            nickname = res.kakaoAccount?.profile?.nickname ?: res.properties?.nickname,
            email = res.kakaoAccount?.email,
            profileImageUrl = res.kakaoAccount?.profile?.profileImageUrl ?: res.properties?.profileImage,
        )
    }
}

class KakaoUnavailableException(message: String) : RuntimeException(message)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class KakaoMeResponse(
    val id: Long?,
    @JsonProperty("kakao_account") val kakaoAccount: KakaoAccount?,
    val properties: KakaoLegacyProps?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class KakaoAccount(
    val email: String?,
    val profile: KakaoProfile?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class KakaoProfile(
    val nickname: String?,
    @JsonProperty("profile_image_url") val profileImageUrl: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class KakaoLegacyProps(
    val nickname: String?,
    @JsonProperty("profile_image") val profileImage: String?,
)
