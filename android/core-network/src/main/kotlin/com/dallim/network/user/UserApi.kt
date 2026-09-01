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
