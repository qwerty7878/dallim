package com.dallim.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Minimal GeoJSON LineString representation, matching every `geoJson` /
 * `thumbnailGeoJson` / `actualGeoJson` / `plannedGeoJson` field in docs/02-api-spec.md.
 *
 * MVP1 only ever emits/consumes LineString (a running route or a recorded track),
 * so this intentionally does not model the full GeoJSON spec (Polygon, Feature, etc).
 */
@Serializable
data class GeoJsonLineString(
    val type: String = "LineString",
    // [ [lng, lat], [lng, lat], ... ] — GeoJSON coordinate order is (lng, lat), NOT (lat, lng).
    val coordinates: List<List<Double>>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Parses a raw `ST_AsGeoJSON(path)` result string (e.g. `{"type":"LineString",...}`)
         * returned by a PostGIS query — see com.dallim.common.PostGis.asGeoJsonExpr.
         */
        fun fromJson(raw: String): GeoJsonLineString = json.decodeFromString(raw)
    }

    /** Converts back to [LatLng] (note the coordinate-order flip: GeoJSON is [lng, lat]). */
    fun toLatLngList(): List<LatLng> = coordinates.map { LatLng(lat = it[1], lng = it[0]) }
}
