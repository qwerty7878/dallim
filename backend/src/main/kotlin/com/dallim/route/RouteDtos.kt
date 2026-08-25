package com.dallim.route

import com.dallim.common.GeoJsonLineString
import kotlinx.serialization.Serializable

// Response DTOs — docs/02-api-spec.md 4장 (routes)

/** GET /routes list item. The list JSON example in docs/02-api-spec.md has no `isSaved` field,
 * but its prose ("로그인 시 저장 여부 포함") does call for one — confirmed policy: include it here
 * to match detail's behavior. Optional-JWT: false when unauthenticated. */
@Serializable
data class RouteSummaryResponse(
    val routeId: String,
    val name: String,
    val emoji: String,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val status: RouteStatus,
    val finisherCount: Int,
    val thumbnailGeoJson: GeoJsonLineString,
    val isSaved: Boolean,
)

@Serializable
data class RouteListResponse(
    val items: List<RouteSummaryResponse>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
)

@Serializable
data class RouteDetailResponse(
    val routeId: String,
    val name: String,
    val emoji: String,
    val geoJson: GeoJsonLineString,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val difficulty: String?,
    val status: RouteStatus,
    val finisherCount: Int,
    val trafficLightCount: Int,
    val elevationGainM: Int,
    val repeatSegmentPercent: Int,
    val runability: Double,
    val isSaved: Boolean,
    val topFeedbackTags: List<String>,
)

@Serializable
data class RouteFinisherItem(
    val runId: String,
    val userNickname: String,
    val thumbnailGeoJson: GeoJsonLineString,
)

@Serializable
data class RouteFinishersResponse(
    val items: List<RouteFinisherItem>,
)
