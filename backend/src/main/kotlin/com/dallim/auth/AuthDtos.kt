package com.dallim.auth

import com.dallim.user.AuthProvider
import kotlinx.serialization.Serializable

// Request DTOs — docs/02-api-spec.md 1장

@Serializable
data class GoogleLoginRequest(val idToken: String)

@Serializable
data class KakaoLoginRequest(val kakaoAccessToken: String)

@Serializable
data class SignupRequest(val email: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

/**
 * POST /auth/logout body. The spec doesn't render an explicit request schema for logout, but
 * "현재 세션의 Refresh Token 폐기" requires the client to tell us which token to delete —
 * mirrored on /auth/refresh's shape for consistency.
 */
@Serializable
data class LogoutRequest(val refreshToken: String)

// Response DTOs

/**
 * Unified shape for all three login paths (Google/Kakao/Email) per docs/02-api-spec.md 1장:
 * "세 경로 모두 응답 스키마를 동일하게 통일". Reused as-is by POST /auth/google, /auth/kakao,
 * /auth/signup, /auth/login.
 */
@Serializable
data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val isNewUser: Boolean,
    val userId: String,
    val provider: AuthProvider,
)

@Serializable
data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String,
)
