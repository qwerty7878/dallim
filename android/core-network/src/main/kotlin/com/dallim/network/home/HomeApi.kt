package com.dallim.network.home

import com.dallim.network.common.ApiResponse
import com.dallim.network.common.GeoJsonLineString
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** docs/02-api-spec.md 3장 — S-10 홈 통합 데이터. */
interface HomeApi {
    @GET("home")
    suspend fun getHome(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
    ): Response<ApiResponse<HomeResponseBody>>
}

@Serializable
data class HomeResponseBody(
    val todaySketch: TodaySketch?,
    val continueRoutes: List<ContinueRoute> = emptyList(),
    val recentRuns: List<RecentRun> = emptyList(),
)

@Serializable
data class TodaySketch(
    val routeId: String,
    val name: String,
    val emoji: String,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val thumbnailGeoJson: GeoJsonLineString,
)

@Serializable
data class ContinueRoute(
    val routeId: String,
    val name: String,
    val status: String,
    val lastCoveragePercent: Int,
)

@Serializable
data class RecentRun(
    val runId: String,
    val distanceKm: Double,
    val completedAt: String,
)
