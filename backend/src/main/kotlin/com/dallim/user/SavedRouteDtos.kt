package com.dallim.user

import kotlinx.serialization.Serializable

// GET /users/me/saved-routes — docs/02-api-spec.md 2장

@Serializable
data class SavedRouteItem(
    val routeId: String,
    val name: String,
    val emoji: String,
    val distanceKm: Double,
    // TODO(backend-dev, run domain round): should reflect whether the user has a COMPLETED
    // RunRecord for this route; the run domain doesn't exist yet this round, so always false.
    val hasRun: Boolean,
)

@Serializable
data class SavedRoutesResponse(
    val items: List<SavedRouteItem>,
    val totalCount: Int,
)
