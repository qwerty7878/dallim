package com.dallim.route

import com.dallim.common.GeoJsonLineString
import kotlinx.serialization.Serializable

// Response DTOs — docs/02-api-spec.md 4장 (routes)

/** GET /routes list item. NOTE: the list JSON example has no `isSaved` field (unlike detail) —
 * only docs/02-api-spec.md's prose mentions "로그인 시 저장 여부 포함" for this endpoint, which
 * conflicts with its own example schema. The example schema is treated as authoritative here;
 * see the round report for the discrepancy. */
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
