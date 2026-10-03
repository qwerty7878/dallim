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
    val weekSummary: WeekSummary = WeekSummary(),
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
    // 자유 러닝(2026-09-26)이면 routeId/emoji는 null, routeName은 "자유 러닝", 썸네일은 실제 궤적.
    val routeId: String? = null,
    val routeName: String,
    val emoji: String? = null,
    val thumbnailGeoJson: GeoJsonLineString,
)

/** 이번 주(월~일, KST) 요약. [runDays]/[todayDayOfWeek]는 ISO 요일 번호(1=월 … 7=일). */
@Serializable
data class WeekSummary(
    val distanceKm: Double = 0.0,
    val runCount: Int = 0,
    val runDays: List<Int> = emptyList(),
    val todayDayOfWeek: Int = 1,
)
