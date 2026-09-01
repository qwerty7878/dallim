package com.dallim.network.common

import kotlinx.serialization.Serializable

/**
 * GeoJSON LineString as returned by the backend for route/run polylines
 * (docs/02-api-spec.md — `thumbnailGeoJson`, `geoJson`, `plannedGeoJson`, `actualGeoJson`).
 * `coordinates` is `[[lng, lat], [lng, lat], ...]` — GeoJSON order is lng first, NOT lat first.
 */
@Serializable
data class GeoJsonLineString(
    val type: String = "LineString",
    val coordinates: List<List<Double>> = emptyList(),
) {
    /** Convenience: coordinates as (lng, lat) pairs. */
    fun toLngLatPairs(): List<Pair<Double, Double>> =
        coordinates.mapNotNull { if (it.size >= 2) it[0] to it[1] else null }
}
