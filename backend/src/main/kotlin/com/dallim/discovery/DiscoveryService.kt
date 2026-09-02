package com.dallim.discovery

import com.dallim.common.BadRequestException
import com.dallim.common.ErrorCodes
import com.dallim.common.GeoJsonLineString
import com.dallim.common.GeoMath
import com.dallim.common.LatLng
import com.dallim.common.OsrmClient
import com.dallim.common.OsrmRouteResult
import com.dallim.common.UnprocessableEntityException
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Course-generation business logic — docs/02-api-spec.md 8장. Both entry points delegate the
 * actual road-network routing to OSRM (com.dallim.common.OsrmClient); this class only handles
 * request validation, the discovery waypoint-placement/retry algorithm, and response shaping.
 *
 * Neither draw-convert nor discovery persists anything to sketch_routes (8장 prose: "프리뷰 응답만
 * 우선 내려준다") — that's an explicitly deferred follow-up.
 */
class DiscoveryService(
    private val osrmClient: OsrmClient,
) {
    companion object {
        private const val MIN_DRAW_POINTS = 10
        private const val WAYPOINT_COUNT = 5
        private const val MAX_ATTEMPTS = 5
        private const val TARGET_TOLERANCE = 0.15
        private const val BEARING_JITTER_DEGREES = 15.0

        // Matches the pace implied by the curated SketchRoute seed data
        // (db/migration/V2__seed_curated_routes.sql: distance_km/estimated_minutes ratios cluster
        // around 6.9-7.1 min/km), used here since docs/02-api-spec.md 8.2 has no explicit pace
        // formula of its own and asks to reuse whatever the existing routes assume.
        private const val ASSUMED_MINUTES_PER_KM = 7.0
    }

    /** POST /routes/draw-convert (docs/02-api-spec.md 8.1). */
    suspend fun convertDrawnPath(drawnPath: GeoJsonLineString): DrawConvertResponse {
        if (drawnPath.coordinates.size < MIN_DRAW_POINTS) {
            throw BadRequestException(ErrorCodes.DRAW_TOO_SHORT, "그림이 너무 짧아요. 조금 더 길게 그려주세요.")
        }

        val matched = osrmClient.match(drawnPath.toLatLngList())
            ?: throw BadRequestException(ErrorCodes.DRAW_MATCH_FAILED, "그림이 도로와 너무 안 맞아요. 다시 그려보세요.")

        return DrawConvertResponse(
            geoJson = matched.geometry,
            distanceKm = matched.distanceMeters.toRoundedKm(),
        )
    }

    /** POST /routes/discovery (docs/02-api-spec.md 8.2). */
    suspend fun generateDiscoveryRoute(request: DiscoveryRequest): DiscoveryResponse {
        val start = LatLng(lat = request.startLat, lng = request.startLng)
        val targetDistanceMeters = request.targetDistanceKm * 1000.0
        var radius = targetDistanceMeters / (2 * PI)

        var lastResult: OsrmRouteResult? = null

        for (attempt in 1..MAX_ATTEMPTS) {
            val loop = listOf(start) + buildWaypoints(start, radius) + start
            val result = runCatching { osrmClient.route(loop) }.getOrNull() ?: continue
            lastResult = result

            val lowerBound = targetDistanceMeters * (1 - TARGET_TOLERANCE)
            val upperBound = targetDistanceMeters * (1 + TARGET_TOLERANCE)
            if (result.distanceMeters in lowerBound..upperBound) break

            if (result.distanceMeters > 0) {
                radius *= targetDistanceMeters / result.distanceMeters
            }
        }

        val finalResult = lastResult
            ?: throw UnprocessableEntityException(
                ErrorCodes.DISCOVERY_NO_ROUTE,
                "요청하신 위치 주변에서 경로를 찾지 못했습니다.",
            )

        val distanceKm = finalResult.distanceMeters.toRoundedKm()
        return DiscoveryResponse(
            geoJson = finalResult.geometry,
            distanceKm = distanceKm,
            estimatedMinutes = (distanceKm * ASSUMED_MINUTES_PER_KM).roundToInt(),
        )
    }

    /**
     * K candidate waypoints spaced 360/K degrees apart around [start] at [radius] meters, each
     * jittered by up to +-[BEARING_JITTER_DEGREES] so the loop isn't a perfect regular polygon
     * (docs/02-api-spec.md 8.2 algorithm step 3).
     */
    private fun buildWaypoints(start: LatLng, radius: Double): List<LatLng> {
        val step = 360.0 / WAYPOINT_COUNT
        return (0 until WAYPOINT_COUNT).map { i ->
            val bearing = step * i + Random.nextDouble(-BEARING_JITTER_DEGREES, BEARING_JITTER_DEGREES)
            GeoMath.destination(start, bearing, radius)
        }
    }

    private fun Double.toRoundedKm(): Double = (this / 1000.0 * 100).roundToInt() / 100.0
}
