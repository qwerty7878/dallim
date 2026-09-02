package com.dallim.network.route

import com.dallim.network.common.ApiResponse
import com.dallim.network.common.GeoJsonLineString
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * docs/02-api-spec.md 4장 — S-11 탐색 / S-16 Route 상세.
 * docs/02-api-spec.md 8장 — S-43~S-45 코스 생성(직접 그리기/AI 자동 생성).
 */
interface RouteApi {
    @GET("routes")
    suspend fun getRoutes(
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radiusKm") radiusKm: Double? = null,
        @Query("minDistanceKm") minDistanceKm: Double? = null,
        @Query("maxDistanceKm") maxDistanceKm: Double? = null,
        @Query("status") status: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Response<ApiResponse<RouteListResponseBody>>

    @GET("routes/{routeId}")
    suspend fun getRouteDetail(@Path("routeId") routeId: String): Response<ApiResponse<RouteDetailResponseBody>>

    @GET("routes/{routeId}/finishers")
    suspend fun getFinishers(@Path("routeId") routeId: String): Response<ApiResponse<FinishersResponseBody>>

    /** S-44 직접 그리기 — 손그림 궤적을 OSRM Map Matching으로 실도로에 스냅한다 (docs/02-api-spec.md 8.1). */
    @POST("routes/draw-convert")
    suspend fun convertDrawnPath(@Body request: RouteDrawConvertRequest): Response<ApiResponse<RouteDrawConvertResponseBody>>

    /** S-45 AI 자동 생성 — 시작 위치 + 목표 거리로 순환 코스를 생성한다 (docs/02-api-spec.md 8.2). */
    @POST("routes/discovery")
    suspend fun discoverRoute(@Body request: RouteDiscoveryRequest): Response<ApiResponse<RouteDiscoveryResponseBody>>
}

@Serializable
data class RouteListItem(
    val routeId: String,
    val name: String,
    val emoji: String,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val status: String,
    val finisherCount: Int,
    val thumbnailGeoJson: GeoJsonLineString,
    /** 로그인 상태에서만 실제 값이 오고, 비로그인 응답에서는 기본값 false로 취급한다 (docs/02-api-spec.md 4장). */
    val isSaved: Boolean = false,
)

@Serializable
data class RouteListResponseBody(
    val items: List<RouteListItem>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
)

@Serializable
data class RouteDetailResponseBody(
    val routeId: String,
    val name: String,
    val emoji: String,
    val geoJson: GeoJsonLineString,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val difficulty: String,
    val status: String,
    val finisherCount: Int,
    val trafficLightCount: Int,
    val elevationGainM: Int,
    val repeatSegmentPercent: Int,
    val runability: Double,
    val isSaved: Boolean,
    val topFeedbackTags: List<String> = emptyList(),
)

@Serializable
data class FinisherThumbnail(
    val runId: String,
    val userNickname: String,
    val thumbnailGeoJson: GeoJsonLineString,
)

@Serializable
data class FinishersResponseBody(val items: List<FinisherThumbnail>)

// --- 8.1 POST /routes/draw-convert ---

@Serializable
data class RouteDrawConvertRequest(val drawnPath: GeoJsonLineString)

@Serializable
data class RouteDrawConvertResponseBody(
    val geoJson: GeoJsonLineString,
    val distanceKm: Double,
)

// --- 8.2 POST /routes/discovery ---

@Serializable
data class RouteDiscoveryRequest(
    val startLng: Double,
    val startLat: Double,
    val targetDistanceKm: Double,
    /** docs/01-feature-spec.md ComfortablePace의 apiValue 중 하나(예: "PACE_6_7"). optional. */
    val pace: String? = null,
)

@Serializable
data class RouteDiscoveryResponseBody(
    val geoJson: GeoJsonLineString,
    val distanceKm: Double,
    val estimatedMinutes: Int,
)
