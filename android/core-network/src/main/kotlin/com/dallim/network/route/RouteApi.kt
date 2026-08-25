package com.dallim.network.route

import com.dallim.network.common.ApiResponse
import com.dallim.network.common.GeoJsonLineString
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** docs/02-api-spec.md 4장 — S-11 탐색 / S-16 Route 상세. */
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
