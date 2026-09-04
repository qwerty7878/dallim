package com.dallim.route

import kotlinx.serialization.Serializable

// GET /routes/places/search response DTOs — docs/02-api-spec.md 12.1.

@Serializable
data class PlaceSearchItem(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
)

@Serializable
data class PlaceSearchResponse(
    val items: List<PlaceSearchItem>,
)
