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
    // 커뮤니티 투표(모양 맞추기) -- docs/달림_화면별_상세기획서_v1.3.md 297행. `name`(공식 이름)과
    // 별개인 자유 텍스트 집계, 상위 5개까지 득표수 내림차순. 투표가 하나도 없으면 빈 배열.
    val shapeVotes: List<ShapeVoteTally>,
    // 비로그인이거나 아직 투표 안 했으면 null, 투표했으면 그 라벨(RouteShapeVoteTable.label).
    val myShapeVote: String?,
)

/** GET /routes/{routeId} `shapeVotes` item / POST /routes/{routeId}/shape-votes response item.
 * `percent` is rounded to the nearest integer against the route's total vote count -- rounding
 * error across all items summing slightly off 100 is acceptable (docs 명시: 과설계 금지). */
@Serializable
data class ShapeVoteTally(val label: String, val percent: Int)

/** POST /routes/{routeId}/shape-votes request body. `label` is trimmed server-side; 1~10 chars
 * after trimming, else 400 VALIDATION_ERROR (see RouteService.submitShapeVote). */
@Serializable
data class ShapeVoteRequest(val label: String)

/** POST /routes/{routeId}/shape-votes response -- the updated aggregate (same shape as
 * RouteDetailResponse.shapeVotes) plus the caller's just-submitted label. */
@Serializable
data class ShapeVoteSubmitResponse(val shapeVotes: List<ShapeVoteTally>, val myLabel: String)

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
