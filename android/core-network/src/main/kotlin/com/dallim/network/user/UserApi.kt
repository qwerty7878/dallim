package com.dallim.network.user

import com.dallim.network.common.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** docs/02-api-spec.md 2장 — 프로필/저장코스. */
interface UserApi {
    @GET("users/nickname-check")
    suspend fun checkNickname(@Query("value") value: String): Response<ApiResponse<NicknameCheckResponseBody>>

    @POST("users/me/profile")
    suspend fun submitProfile(@Body request: ProfileRequest): Response<ApiResponse<ProfileResponseBody>>

    @GET("users/me")
    suspend fun getMe(): Response<ApiResponse<UserMeResponseBody>>

    @GET("users/me/saved-routes")
    suspend fun getSavedRoutes(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Response<ApiResponse<SavedRoutesResponseBody>>

    @POST("users/me/saved-routes/{routeId}")
    suspend fun saveRoute(@Path("routeId") routeId: String): Response<ApiResponse<Unit>>

    @DELETE("users/me/saved-routes/{routeId}")
    suspend fun unsaveRoute(@Path("routeId") routeId: String): Response<ApiResponse<Unit>>

    /**
     * 현재 기기의 FCM 토큰을 등록(upsert) — docs/02-api-spec.md 10.1(폰 시스템 푸시, 알림 2단계).
     * 같은 토큰이 이미 있으면 마지막 등록 시각만 갱신되고 중복 저장되지 않는 건 서버 책임이라,
     * 클라이언트는 토큰을 새로 받을 때마다(또는 앱 시작/로그인 성공 시) 그냥 다시 호출하면 된다.
     * 무효 토큰 삭제 API는 서버가 발송 실패 시 조용히 처리하므로 클라이언트에 두지 않는다(10.3).
     */
    @POST("users/me/device-tokens")
    suspend fun registerDeviceToken(@Body request: DeviceTokenRequest): Response<ApiResponse<Unit>>
}

@Serializable
data class NicknameCheckResponseBody(val available: Boolean)

/**
 * `gender` is intentionally present ONLY here (request DTO) and never in any response DTO —
 * CLAUDE.md rule 2 / docs/02-api-spec.md 2장 note. Do not add it to [UserMeResponseBody] or any
 * other response type even during refactors.
 */
@Serializable
data class ProfileRequest(
    val nickname: String,
    val avatarId: String,
    val runningExperience: String,
    val comfortablePace: String,
    val gender: String,
)

@Serializable
data class ProfileResponseBody(val userId: String, val nickname: String)

@Serializable
data class UserMeResponseBody(
    val userId: String,
    val nickname: String,
    val avatarId: String,
    val runningExperience: String,
    val comfortablePace: String,
    val totalRuns: Int,
    val totalDistanceKm: Double,
)

@Serializable
data class SavedRouteItem(
    val routeId: String,
    val name: String,
    val emoji: String,
    val distanceKm: Double,
    val hasRun: Boolean,
)

@Serializable
data class SavedRoutesResponseBody(val items: List<SavedRouteItem>, val totalCount: Int)

/** docs/02-api-spec.md 10.1 — `platform`은 이번 라운드에 안드로이드만 존재해 항상 `"ANDROID"`. */
@Serializable
data class DeviceTokenRequest(
    val fcmToken: String,
    val platform: String = "ANDROID",
)
