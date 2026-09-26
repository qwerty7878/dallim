package com.dallim.user

import com.dallim.common.GeoJsonLineString
import kotlinx.serialization.Serializable

// GET /users/me/saved-routes — docs/02-api-spec.md 2장

@Serializable
data class SavedRouteItem(
    val routeId: String,
    val name: String,
    val emoji: String,
    val distanceKm: Double,
    // Whether this user has at least one COMPLETED RunRecord against this route — see
    // com.dallim.user.SavedRouteService.listSaved.
    val hasRun: Boolean,
    // Filled in by SavedRouteService.listSaved via RouteRepository.findThumbnailsByIds, same
    // "cross-domain access at the service layer" convention as [hasRun] — this repository's own
    // query is pure Exposed DSL and never touches the `path` geometry column directly
    // (docs/03-design-system.md §3.2 "모든 코스 카드에 필수").
    val thumbnailGeoJson: GeoJsonLineString,
)

@Serializable
data class SavedRoutesResponse(
    val items: List<SavedRouteItem>,
    val totalCount: Int,
)
