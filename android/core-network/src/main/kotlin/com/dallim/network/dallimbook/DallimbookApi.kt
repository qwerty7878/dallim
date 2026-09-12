package com.dallim.network.dallimbook

import com.dallim.network.common.ApiResponse
import com.dallim.network.common.GeoJsonLineString
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** docs/02-api-spec.md 6장 — S-40 달림북 그리드. */
interface DallimbookApi {
    @GET("users/me/runs")
    suspend fun getMyRuns(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Response<ApiResponse<DallimbookResponseBody>>
}

@Serializable
data class DallimbookRunItem(
    val runId: String,
    val routeName: String,
    val distanceKm: Double,
    val completedAt: String,
    val thumbnailGeoJson: GeoJsonLineString,
)

@Serializable
data class DallimbookResponseBody(
    val items: List<DallimbookRunItem>,
    val totalCount: Int,
    val totalDistanceKm: Double,
    /** 완주 기록이 하나도 없으면 null. 단위: 초/km. */
    val bestPaceSecPerKm: Int? = null,
    /** 완주 기록이 하나도 없으면 null. 단위: 초/km. */
    val averagePaceSecPerKm: Int? = null,
)
