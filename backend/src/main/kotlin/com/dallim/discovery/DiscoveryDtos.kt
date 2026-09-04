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
    // Required for every mode's request shape, but docs/02-api-spec.md 13.1 has SHAPE mode ignore
    // it entirely — SHAPE's size comes from `size` instead (13.1.1). Still required here (not
    // nullable) so LOOP/POINT_TO_POINT keep their existing non-null contract.
    val targetDistanceKm: Double,
    // Optional — accepted for future personalization/logging per docs/02-api-spec.md 8.2, not
    // yet factored into the routing algorithm itself.
    val pace: String? = null,
    // docs/02-api-spec.md 11.2 — 0-3 required waypoints the generated loop must pass through.
    // Each replaces whichever of the K candidate waypoint slots is angularly closest to it
    // (greedily matched so two required points never contend for the same slot). Also usable with
    // POINT_TO_POINT mode (11.6): sorted by (distance-to-start - distance-to-end) instead of the
    // K-slot bearing system, then routed start -> waypoints -> end in one OSRM call.
    val requiredWaypoints: List<LatLngDto> = emptyList(),
    // docs/02-api-spec.md 11.4/13.1 — "LOOP" (default) | "POINT_TO_POINT" | "SHAPE". Raw string
    // validated in DiscoveryService, same convention as com.dallim.run.RunService.updateStatus's
    // statusRaw.
    val mode: String = "LOOP",
    // Required (both) when mode == "POINT_TO_POINT"; ignored otherwise.
    val endLat: Double? = null,
    val endLng: Double? = null,
    // docs/02-api-spec.md 13.1 — required when mode == "SHAPE": "HEART" | "CIRCLE" | "DROP"
    // (com.dallim.discovery.ShapeType; STAR is deliberately unregistered — 13.3). Ignored
    // otherwise.
    val shapeType: String? = null,
    // docs/02-api-spec.md 13.1/13.1.1 — only meaningful when mode == "SHAPE". "S" | "M" | "L",
    // default "M" when omitted from the request JSON (an invalid non-null value, e.g. "XL", is
    // still a 400 VALIDATION_ERROR — validated in DiscoveryService). Fixes the template's absolute
    // scale directly (templateRadiusMeters 1000/1500/2200) — replaces the old `priority` field,
    // which no longer means anything now that there's no target-distance retry loop to prioritize.
    val size: String = "M",
)

@Serializable
data class DiscoveryResponse(
    val geoJson: GeoJsonLineString,
    val distanceKm: Double,
    val estimatedMinutes: Int,
)
