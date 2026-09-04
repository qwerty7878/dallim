package com.dallim.discovery

import com.dallim.common.GeoJsonLineString
import com.dallim.common.LatLng
import kotlinx.serialization.Serializable

// Request/response DTOs — docs/02-api-spec.md 8장 (course generation: draw-convert, discovery)
// and 11장 (draw-convert closeLoop / discovery requiredWaypoints option extensions).

@Serializable
data class DrawConvertRequest(
    val drawnPath: GeoJsonLineString,
    // docs/02-api-spec.md 11.1 — only meaningful when the user explicitly drew with a
    // start=finish intent; the server never infers this from the shape itself.
    val closeLoop: Boolean = false,
)

@Serializable
data class DrawConvertResponse(
    val geoJson: GeoJsonLineString,
    val distanceKm: Double,
)

/** Wire-format lat/lng pair, e.g. docs/02-api-spec.md 11.2's `requiredWaypoints` entries. */
@Serializable
data class LatLngDto(val lat: Double, val lng: Double) {
    fun toLatLng() = LatLng(lat = lat, lng = lng)
}

@Serializable
data class DiscoveryRequest(
    val startLng: Double,
    val startLat: Double,
    val targetDistanceKm: Double,
    // Optional — accepted for future personalization/logging per docs/02-api-spec.md 8.2, not
    // yet factored into the routing algorithm itself.
    val pace: String? = null,
    // docs/02-api-spec.md 11.2 — 0-3 required waypoints the generated loop must pass through.
    // Each replaces whichever of the K candidate waypoint slots is angularly closest to it
    // (greedily matched so two required points never contend for the same slot).
    val requiredWaypoints: List<LatLngDto> = emptyList(),
)

@Serializable
data class DiscoveryResponse(
    val geoJson: GeoJsonLineString,
    val distanceKm: Double,
    val estimatedMinutes: Int,
)
