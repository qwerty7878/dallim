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

        // docs/02-api-spec.md 11.2 — must stay below WAYPOINT_COUNT so at least a couple of free
        // (radius-adjustable) slots always remain to hit the target distance.
        private const val MAX_REQUIRED_WAYPOINTS = 3

        // docs/02-api-spec.md 11.1 — below this gap between a matched trace's first/last points,
        // closeLoop treats the loop as already closed and skips the extra OSRM round trip.
        private const val CLOSE_LOOP_GAP_THRESHOLD_METERS = 15.0

        // Matches the pace implied by the curated SketchRoute seed data
        // (db/migration/V2__seed_curated_routes.sql: distance_km/estimated_minutes ratios cluster
        // around 6.9-7.1 min/km), used here since docs/02-api-spec.md 8.2 has no explicit pace
        // formula of its own and asks to reuse whatever the existing routes assume.
        private const val ASSUMED_MINUTES_PER_KM = 7.0
    }

    /** POST /routes/draw-convert (docs/02-api-spec.md 8.1, closeLoop option per 11.1). */
    suspend fun convertDrawnPath(request: DrawConvertRequest): DrawConvertResponse {
        val drawnPath = request.drawnPath
        if (drawnPath.coordinates.size < MIN_DRAW_POINTS) {
            throw BadRequestException(ErrorCodes.DRAW_TOO_SHORT, "그림이 너무 짧아요. 조금 더 길게 그려주세요.")
        }

        val matched = osrmClient.match(drawnPath.toLatLngList())
            ?: throw BadRequestException(ErrorCodes.DRAW_MATCH_FAILED, "그림이 도로와 너무 안 맞아요. 다시 그려보세요.")

        val result = if (request.closeLoop) closeLoopIfNeeded(matched) else matched

        return DrawConvertResponse(
            geoJson = result.geometry,
            distanceKm = result.distanceMeters.toRoundedKm(),
        )
    }

    /**
     * docs/02-api-spec.md 11.1 — if the matched trace's first/last points are more than
     * [CLOSE_LOOP_GAP_THRESHOLD_METERS] apart, connects them with one extra OSRM `/route` call and
     * appends that segment's geometry/distance. Already-close traces and a failed closing call are
     * both left as-is (never turns into an error) — "닫아주면 좋고 아니어도 그림 자체는 유효".
     */
    private suspend fun closeLoopIfNeeded(matched: OsrmRouteResult): OsrmRouteResult {
        val points = matched.geometry.toLatLngList()
        val first = points.firstOrNull() ?: return matched
        val last = points.lastOrNull() ?: return matched

        if (GeoMath.haversineMeters(last, first) <= CLOSE_LOOP_GAP_THRESHOLD_METERS) return matched

        val closingSegment = runCatching { osrmClient.route(listOf(last, first)) }.getOrNull() ?: return matched

        return OsrmRouteResult(
            distanceMeters = matched.distanceMeters + closingSegment.distanceMeters,
            geometry = GeoJsonLineString(
                coordinates = matched.geometry.coordinates + closingSegment.geometry.coordinates,
            ),
        )
    }

    /** POST /routes/discovery (docs/02-api-spec.md 8.2, requiredWaypoints option per 11.2). */
    suspend fun generateDiscoveryRoute(request: DiscoveryRequest): DiscoveryResponse {
        if (request.requiredWaypoints.size > MAX_REQUIRED_WAYPOINTS) {
            throw BadRequestException(
                ErrorCodes.VALIDATION_ERROR,
                "필수 경유지는 최대 ${MAX_REQUIRED_WAYPOINTS}개까지 지정할 수 있어요.",
            )
        }

        val start = LatLng(lat = request.startLat, lng = request.startLng)
        val targetDistanceMeters = request.targetDistanceKm * 1000.0
        var radius = targetDistanceMeters / (2 * PI)

        // Fixed vs radius-adjustable slots, decided once up front — 11.2: the retry loop below
        // only ever moves the Free slots' distance from start, never any Fixed one.
        val slots = buildWaypointSlots(start, request.requiredWaypoints.map { it.toLatLng() })

        var lastResult: OsrmRouteResult? = null

        for (attempt in 1..MAX_ATTEMPTS) {
            val waypoints = slots.map { it.resolve(start, radius) }
            val loop = listOf(start) + waypoints + start
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

    /** One of the K candidate loop waypoints: either pinned to a caller-supplied point, or free to move with [radius]. */
    private sealed interface WaypointSlot {
        fun resolve(start: LatLng, radius: Double): LatLng

        data class Fixed(val point: LatLng) : WaypointSlot {
            override fun resolve(start: LatLng, radius: Double) = point
        }

        data class Free(val bearingDegrees: Double) : WaypointSlot {
            override fun resolve(start: LatLng, radius: Double) = GeoMath.destination(start, bearingDegrees, radius)
        }
    }

    /**
     * Builds the K waypoint slots (docs/02-api-spec.md 8.2 algorithm step 3): bearings spaced
     * 360/K degrees apart around [start], each jittered by up to +-[BEARING_JITTER_DEGREES] so the
     * loop isn't a perfect regular polygon.
     *
     * When [requiredWaypoints] is given (11.2, 0-[MAX_REQUIRED_WAYPOINTS]), each one is pinned to
     * whichever slot's bearing from [start] is angularly closest to its own — assigned greedily by
     * smallest angular difference first, so two required points never contend for the same slot.
     * Every unmatched slot stays [Free]. Because slot order already follows bearing order, the
     * final loop visits the fixed points in roughly the same angular sequence around [start].
     */
    private fun buildWaypointSlots(start: LatLng, requiredWaypoints: List<LatLng>): List<WaypointSlot> {
        val step = 360.0 / WAYPOINT_COUNT
        val bearings = (0 until WAYPOINT_COUNT).map { i ->
            step * i + Random.nextDouble(-BEARING_JITTER_DEGREES, BEARING_JITTER_DEGREES)
        }

        if (requiredWaypoints.isEmpty()) {
            return bearings.map { WaypointSlot.Free(it) }
        }

        val fixedBySlotIndex = mutableMapOf<Int, LatLng>()
        val takenSlotIndices = mutableSetOf<Int>()
        val assignedWaypointIndices = mutableSetOf<Int>()

        val candidatePairs = requiredWaypoints.indices.flatMap { wpIndex ->
            val targetBearing = GeoMath.bearingDegrees(start, requiredWaypoints[wpIndex])
            bearings.indices.map { slotIndex ->
                Triple(wpIndex, slotIndex, angularDifferenceDegrees(bearings[slotIndex], targetBearing))
            }
        }.sortedBy { it.third }

        for ((wpIndex, slotIndex, _) in candidatePairs) {
            if (wpIndex in assignedWaypointIndices || slotIndex in takenSlotIndices) continue
            fixedBySlotIndex[slotIndex] = requiredWaypoints[wpIndex]
            takenSlotIndices += slotIndex
            assignedWaypointIndices += wpIndex
            if (assignedWaypointIndices.size == requiredWaypoints.size) break
        }

        return bearings.mapIndexed { i, bearing ->
            fixedBySlotIndex[i]?.let { WaypointSlot.Fixed(it) } ?: WaypointSlot.Free(bearing)
        }
    }

    /** Smallest angle (0..180) between two compass bearings in degrees. */
    private fun angularDifferenceDegrees(a: Double, b: Double): Double {
        val diff = Math.abs(a - b) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }

    private fun Double.toRoundedKm(): Double = (this / 1000.0 * 100).roundToInt() / 100.0
}
