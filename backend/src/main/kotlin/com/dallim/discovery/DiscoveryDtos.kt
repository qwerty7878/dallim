package com.dallim.discovery

import com.dallim.common.GeoJsonLineString
import kotlinx.serialization.Serializable

// Request/response DTOs — docs/02-api-spec.md 8장 (course generation: draw-convert, discovery)

@Serializable
data class DrawConvertRequest(
    val drawnPath: GeoJsonLineString,
)

@Serializable
data class DrawConvertResponse(
    val geoJson: GeoJsonLineString,
    val distanceKm: Double,
)

@Serializable
data class DiscoveryRequest(
    val startLng: Double,
    val startLat: Double,
    val targetDistanceKm: Double,
    // Optional — accepted for future personalization/logging per docs/02-api-spec.md 8.2, not
    // yet factored into the routing algorithm itself.
    val pace: String? = null,
)

@Serializable
data class DiscoveryResponse(
    val geoJson: GeoJsonLineString,
    val distanceKm: Double,
    val estimatedMinutes: Int,
)
