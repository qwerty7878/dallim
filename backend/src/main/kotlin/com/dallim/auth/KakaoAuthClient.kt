package com.dallim.auth

import com.dallim.common.ErrorCodes
import com.dallim.common.UnauthorizedException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Verifies a Kakao Access Token by calling Kakao's user info API server-side
 * (GET https://kapi.kakao.com/v2/user/me), per docs/02-api-spec.md 1장 POST /auth/kakao and
 * docs/01-feature-spec.md 2.2.A. Kakao email consent is optional, so `kakaoAccount.email`
 * may be null — callers must treat that as "cannot determine account linking" and create a
 * separate account rather than guessing (see docs/02-api-spec.md 계정 통합 정책).
 */
class KakaoAuthClient(
    private val httpClient: HttpClient,
    private val userInfoUrl: String,
) {
    @Serializable
    data class KakaoUserInfo(
        val id: Long,
        @SerialName("kakao_account") val kakaoAccount: KakaoAccount? = null,
    )

    @Serializable
    data class KakaoAccount(
        val email: String? = null,
        @SerialName("is_email_verified") val isEmailVerified: Boolean? = null,
    )

    suspend fun getUser(accessToken: String): KakaoUserInfo {
        val response =
            try {
                httpClient.get(userInfoUrl) {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            } catch (e: Exception) {
                throw UnauthorizedException(ErrorCodes.INVALID_KAKAO_TOKEN, "카카오 토큰 검증에 실패했습니다.")
            }

        if (response.status != HttpStatusCode.OK) {
            throw UnauthorizedException(ErrorCodes.INVALID_KAKAO_TOKEN, "카카오 토큰 검증에 실패했습니다.")
        }

        return try {
            response.body<KakaoUserInfo>()
        } catch (e: Exception) {
            throw UnauthorizedException(ErrorCodes.INVALID_KAKAO_TOKEN, "카카오 토큰 검증에 실패했습니다.")
        }
    }
}
