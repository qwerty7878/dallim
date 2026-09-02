package com.dallim.network.auth

import com.dallim.network.common.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * docs/02-api-spec.md 1장 — Google/이메일 로그인 2종 + 회원가입/로그아웃. (Kakao는 제외하기로
 * 결정되어 이 클라이언트에는 연동하지 않는다.)
 * All login paths return the same [AuthResponseBody] shape by design so S-03~S-06
 * onboarding can branch on `isNewUser` without caring which provider was used.
 */
interface AuthApi {
    @POST("auth/google")
    suspend fun loginWithGoogle(@Body request: GoogleLoginRequest): Response<ApiResponse<AuthResponseBody>>

    @POST("auth/signup")
    suspend fun signup(@Body request: EmailSignupRequest): Response<ApiResponse<AuthResponseBody>>

    @POST("auth/login")
    suspend fun login(@Body request: EmailLoginRequest): Response<ApiResponse<AuthResponseBody>>

    @POST("auth/logout")
    suspend fun logout(): Response<ApiResponse<Unit>>
}

@Serializable
data class GoogleLoginRequest(val idToken: String)

@Serializable
data class EmailSignupRequest(val email: String, val password: String)

@Serializable
data class EmailLoginRequest(val email: String, val password: String)

@Serializable
data class AuthResponseBody(
    val accessToken: String,
    val refreshToken: String,
    val isNewUser: Boolean,
    val userId: String,
    val provider: String,
)
