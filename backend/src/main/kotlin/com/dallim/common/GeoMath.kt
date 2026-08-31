package com.dallim.common

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A lat/lng point in WGS84 degrees. Deliberately separate from [GeoJsonLineString] (which is a
 * wire-format DTO) — this is the plain value type the run-judgement math (docs/01-feature-spec.md
 * 2.2.D) operates on, and per 2.4 ("Haversine 거리 계산... 별도 유틸 클래스로 분리, 추후 discovery
 * 모듈에서도 재사용") is meant to be reused outside the run domain too.
 */
data class LatLng(val lat: Double, val lng: Double)

/**
 * Geometry utilities shared by the run-judgement algorithm (docs/01-feature-spec.md 2.2.D) and,
 * per 2.4, intended for reuse by a future discovery module. Every function here is pure — no I/O,
 * no framework types — so it is directly unit-testable.
 */
object GeoMath {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** Great-circle distance between two points, in meters (Haversine formula). */
    fun haversineMeters(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Sum of consecutive Haversine distances along an ordered point sequence, in meters. */
    fun pathLengthMeters(points: List<LatLng>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) total += haversineMeters(points[i - 1], points[i])
        return total
    }

    /**
     * Re-samples an ordered polyline into exactly [count] points, evenly spaced by arc length
     * from start to end inclusive, via linear (planar) interpolation between the two original
     * vertices bracketing each target distance. Used to turn a planned route LineString into N
     * evenly spaced checkpoints for Route Coverage (docs/01-feature-spec.md 2.2.D step 2), and to
     * bound the point count fed into the O(n*m) Fréchet computation (step 4).
     *
     * The planar-interpolation approximation error is negligible at the tens-of-meters scale
     * these checks operate at.
     */
    fun resample(points: List<LatLng>, count: Int): List<LatLng> {
        require(count >= 2) { "count must be >= 2" }
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return List(count) { points[0] }

        val cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cumulative[i] = cumulative[i - 1] + haversineMeters(points[i - 1], points[i])
        }
        val total = cumulative.last()
        if (total == 0.0) return List(count) { points[0] }

        return (0 until count).map { i ->
            val targetDist = total * i / (count - 1)
            var idx = cumulative.indexOfLast { it <= targetDist }
            if (idx < 0) idx = 0
            if (idx >= points.size - 1) idx = points.size - 2

            val segStart = cumulative[idx]
            val segLen = cumulative[idx + 1] - segStart
            val t = if (segLen <= 0.0) 0.0 else ((targetDist - segStart) / segLen).coerceIn(0.0, 1.0)

            LatLng(
                lat = points[idx].lat + (points[idx + 1].lat - points[idx].lat) * t,
                lng = points[idx].lng + (points[idx + 1].lng - points[idx].lng) * t,
            )
        }
    }

    /** Rounds a 0.0..100.0 percent value to a 0..100 Int, clamping defensively. */
    fun Double.toPercentInt(): Int = roundToInt().coerceIn(0, 100)
}
