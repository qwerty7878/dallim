package com.dallim.dallimbook

import com.dallim.common.GeoJsonLineString
import kotlinx.serialization.Serializable

// GET /users/me/runs — docs/02-api-spec.md 6장 (S-40 달림북 그리드). Field names must match
// android/core-network/src/main/kotlin/com/dallim/network/dallimbook/DallimbookApi.kt exactly.

@Serializable
data class DallimbookRunItem(
    val runId: String,
    val routeName: String,
    val distanceKm: Double,
    val completedAt: String,
    val thumbnailGeoJson: GeoJsonLineString,
)

@Serializable
data class DallimbookResponse(
    val items: List<DallimbookRunItem>,
    val totalCount: Int,
    val totalDistanceKm: Double,
)
