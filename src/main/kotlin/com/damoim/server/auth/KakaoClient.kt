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
data class KakaoProperties(
    val userInfoUrl: String,
    val tokenInfoUrl: String,
    /** 우리 서비스의 카카오 앱 식별자. 미주입(0) 시 부팅 실패(fail-fast). */
    val appId: Long,
)

/** 카카오 사용자 식별 결과(우리가 신뢰하는 값 — 카카오가 응답). */
data class KakaoUserInfo(
    val kakaoUserId: String,
    val nickname: String?,
    val email: String?,
    val profileImageUrl: String?,
)

/**
 * 클라가 보낸 카카오 access token을 **서버가 카카오 API로 재검증**한다.
 * (1) access_token_info로 토큰이 **우리 앱(app_id)** 에 발급된 것인지 확인(confused-deputy 방지) →
 * (2) user/me로 프로필 조회. 유효하지 않으면 KAKAO_AUTH_FAILED. 클라의 신원 주장을 신뢰하지 않는다.
 */
@Component
class KakaoClient(private val props: KakaoProperties) {

    init {
        require(props.appId > 0) {
            "app.kakao.app-id(KAKAO_APP_ID)는 필수입니다. 카카오 앱 식별자를 주입하세요."
        }
    }

    // 커넥트/리드 타임아웃 지정 — 카카오 지연 시 톰캣 워커 스레드 고갈 방지(비인증 엔드포인트 보호).
    // 두 엔드포인트를 절대 URL로 호출하므로 baseUrl은 두지 않는다.
    private val restClient: RestClient = RestClient.builder()
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(2000)   // ms
                setReadTimeout(3000)      // ms
            },
        )
        .build()

    fun fetchUserInfo(kakaoAccessToken: String): KakaoUserInfo {
        // 1) 토큰의 발급 대상 앱 검증 — 다른 카카오 앱 토큰으로의 로그인 차단
        val tokenInfo = call(props.tokenInfoUrl, kakaoAccessToken, KakaoTokenInfoResponse::class.java)
        if (tokenInfo?.appId == null || tokenInfo.appId != props.appId) {
            throw UnauthorizedException("카카오 인증에 실패했습니다.", "KAKAO_AUTH_FAILED")
        }
        // 2) 프로필 조회
        val res = call(props.userInfoUrl, kakaoAccessToken, KakaoMeResponse::class.java)
        val id = res?.id ?: throw UnauthorizedException("카카오 인증에 실패했습니다.", "KAKAO_AUTH_FAILED")
        return KakaoUserInfo(
            kakaoUserId = id.toString(),
            nickname = res.kakaoAccount?.profile?.nickname ?: res.properties?.nickname,
            email = res.kakaoAccount?.email,
            profileImageUrl = res.kakaoAccount?.profile?.profileImageUrl ?: res.properties?.profileImage,
        )
    }

    private fun <T> call(url: String, kakaoAccessToken: String, type: Class<T>): T? = try {
        restClient.get()
            .uri(url)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $kakaoAccessToken")
            .retrieve()
            .onStatus({ it.is4xxClientError }) { _, _ ->
                throw UnauthorizedException("카카오 인증에 실패했습니다.", "KAKAO_AUTH_FAILED")
            }
            .body(type)
    } catch (e: UnauthorizedException) {
        throw e
    } catch (e: Exception) {
        // 네트워크/5xx 등은 상위에서 502로 처리하도록 런타임 예외 전파(민감정보 미포함 메시지)
        throw KakaoUnavailableException("카카오 인증 서버에 연결할 수 없습니다.")
    }
}

class KakaoUnavailableException(message: String) : RuntimeException(message)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class KakaoTokenInfoResponse(
    @JsonProperty("app_id") val appId: Long?,
    val id: Long?,
)

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
