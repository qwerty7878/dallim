package com.dallim.user

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
)

@Serializable
data class SavedRoutesResponse(
    val items: List<SavedRouteItem>,
    val totalCount: Int,
)
