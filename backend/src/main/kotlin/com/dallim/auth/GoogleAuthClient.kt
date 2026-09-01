package com.dallim.auth

import com.dallim.common.ErrorCodes
import com.dallim.common.UnauthorizedException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Verifies a Google ID Token by calling Google's tokeninfo endpoint
 * (https://oauth2.googleapis.com/tokeninfo?id_token=...). Google performs the public-key
 * signature check server-side and returns the decoded claims — this satisfies
 * docs/01-feature-spec.md 2.2.A "ID Token을 Google 공개키로 서명 검증" without pulling in a
 * JWKS client library. We additionally pin `aud` to the configured OAuth client id so a
 * token minted for a different app can't be replayed here.
 */
class GoogleAuthClient(
    private val httpClient: HttpClient,
    private val googleClientId: String,
) {
    @Serializable
    data class TokenInfo(
        val sub: String,
        val aud: String? = null,
        val email: String? = null,
        @SerialName("email_verified") val emailVerified: String? = null,
    )

    suspend fun verify(idToken: String): TokenInfo {
        val response =
            try {
                httpClient.get("https://oauth2.googleapis.com/tokeninfo") {
                    parameter("id_token", idToken)
                }
            } catch (e: Exception) {
                throw UnauthorizedException(ErrorCodes.INVALID_GOOGLE_TOKEN, "구글 토큰 검증에 실패했습니다.")
            }

        if (response.status != HttpStatusCode.OK) {
            throw UnauthorizedException(ErrorCodes.INVALID_GOOGLE_TOKEN, "구글 토큰 검증에 실패했습니다.")
        }

        val info =
            try {
                response.body<TokenInfo>()
            } catch (e: Exception) {
                throw UnauthorizedException(ErrorCodes.INVALID_GOOGLE_TOKEN, "구글 토큰 검증에 실패했습니다.")
            }

        if (info.aud.isNullOrBlank() || info.aud != googleClientId) {
            throw UnauthorizedException(ErrorCodes.INVALID_GOOGLE_TOKEN, "구글 토큰 검증에 실패했습니다.")
        }

        return info
    }
}
