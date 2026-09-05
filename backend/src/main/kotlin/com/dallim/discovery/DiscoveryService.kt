package com.dallim.discovery

import com.dallim.common.BadRequestException
import com.dallim.common.ErrorCodes
import com.dallim.common.FrechetDistance
import com.dallim.common.GeoJsonLineString
import com.dallim.common.GeoMath
import com.dallim.common.LatLng
import com.dallim.common.OsrmClient
import com.dallim.common.OsrmRouteResult
import com.dallim.common.UnprocessableEntityException
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
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
    // docs/02-api-spec.md 13.2 — mode: "SHAPE" routes only through this dedicated instance
    // (arterial-sidewalk-preferring profile, service-belt-only dataset); LOOP/POINT_TO_POINT/
    // requiredWaypoints all stay on [osrmClient]. Defaults to [osrmClient] so every pre-existing
    // call site/test (none of which exercise SHAPE mode) keeps working unchanged.
    private val osrmShapeClient: OsrmClient = osrmClient,
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

        // docs/02-api-spec.md 11.4 — jitter applied to the detour waypoint's perpendicular bearing
        // in point-to-point mode, same purpose as BEARING_JITTER_DEGREES for the loop candidates.
        private const val POINT_TO_POINT_BEARING_JITTER_DEGREES = 20.0

        // docs/02-api-spec.md 13.1.1 — size -> templateRadiusMeters. Fixed, measured scale per
        // tier; replaces the old distance-target retry loop entirely (there's no target to hit
        // anymore, so there's nothing to retry-adjust the scale against).
        private val SHAPE_SIZE_RADIUS_METERS = mapOf(
            "S" to 1000.0,
            "M" to 1500.0,
            "L" to 2200.0,
        )

        // Meters per degree of latitude/longitude at the equator (2*PI*EARTH_RADIUS_METERS/360,
        // same EARTH_RADIUS_METERS as GeoMath) — used to convert a ShapeTemplate's tiny-degree
        // offsets (deliberately centered near lat=0, see ShapeTemplate's doc comment) into an
        // undistorted local meters plane for rotation/scaling in generateShapeRoute.
        private const val DEGREES_TO_METERS = PI / 180.0 * 6_371_000.0

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

    /**
     * POST /routes/discovery (docs/02-api-spec.md 8.2/11.2/11.4/11.6). Validates `mode` and the
     * fields it requires, then dispatches to the loop or point-to-point algorithm — the latter
     * further split by whether `requiredWaypoints` is present (11.6) or not (11.4) — both funnel
     * through [respond] to build the final [DiscoveryResponse] the same way.
     */
    suspend fun generateDiscoveryRoute(request: DiscoveryRequest): DiscoveryResponse {
        if (request.requiredWaypoints.size > MAX_REQUIRED_WAYPOINTS) {
            throw BadRequestException(
                ErrorCodes.VALIDATION_ERROR,
                "필수 경유지는 최대 ${MAX_REQUIRED_WAYPOINTS}개까지 지정할 수 있어요.",
            )
        }

        val start = LatLng(lat = request.startLat, lng = request.startLng)
        val targetDistanceMeters = request.targetDistanceKm * 1000.0

        val result = when (request.mode) {
            "LOOP" -> generateLoopRoute(request, start, targetDistanceMeters)
            "POINT_TO_POINT" -> {
                val end = requireEndPoint(request)
                if (request.requiredWaypoints.isEmpty()) {
                    generatePointToPointRoute(start, end, targetDistanceMeters)
                } else {
                    generatePointToPointWithWaypointsRoute(
                        start = start,
                        end = end,
                        requiredWaypoints = request.requiredWaypoints.map { it.toLatLng() },
                        targetDistanceMeters = targetDistanceMeters,
                    )
                }
            }
            "SHAPE" -> {
                validateNoShapeConflicts(request)
                generateShapeRoute(
                    shapeType = requireShapeType(request),
                    templateRadiusMeters = requireShapeSizeRadiusMeters(request),
                    start = start,
                )
            }
            else -> throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "mode는 LOOP, POINT_TO_POINT, SHAPE만 허용됩니다.")
        }

        return respond(result)
    }

    /** docs/02-api-spec.md 11.4 — both endLat/endLng required together when mode == POINT_TO_POINT. */
    private fun requireEndPoint(request: DiscoveryRequest): LatLng {
        val lat = request.endLat
        val lng = request.endLng
        if (lat == null || lng == null) {
            throw BadRequestException(
                ErrorCodes.VALIDATION_ERROR,
                "point-to-point 모드는 endLat/endLng가 모두 필요해요.",
            )
        }
        return LatLng(lat = lat, lng = lng)
    }

    /**
     * docs/02-api-spec.md 13.1 — required, must be a registered ShapeType name, when mode == SHAPE.
     * `STAR` is deliberately not a registered entry (13.3), so it falls through to this same 400.
     */
    private fun requireShapeType(request: DiscoveryRequest): ShapeType =
        ShapeType.fromRequestValue(request.shapeType)
            ?: throw BadRequestException(
                ErrorCodes.VALIDATION_ERROR,
                "shapeType은 HEART, CIRCLE, DROP 중 하나여야 해요.",
            )

    /** docs/02-api-spec.md 13.1.1 — "S" | "M" | "L" only (default "M" already applied by DiscoveryRequest). */
    private fun requireShapeSizeRadiusMeters(request: DiscoveryRequest): Double =
        SHAPE_SIZE_RADIUS_METERS[request.size]
            ?: throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "size는 S, M, L 중 하나여야 해요.")

    /** docs/02-api-spec.md 13.4 — SHAPE mode doesn't compose with requiredWaypoints or point-to-point (this round). */
    private fun validateNoShapeConflicts(request: DiscoveryRequest) {
        if (request.requiredWaypoints.isNotEmpty() || request.endLat != null || request.endLng != null) {
            throw BadRequestException(
                ErrorCodes.VALIDATION_ERROR,
                "SHAPE 모드는 requiredWaypoints/endLat/endLng와 함께 사용할 수 없어요.",
            )
        }
    }

    /** docs/02-api-spec.md 8.2 — the original always-loops-back-to-start algorithm, requiredWaypoints per 11.2. */
    private suspend fun generateLoopRoute(
        request: DiscoveryRequest,
        start: LatLng,
        targetDistanceMeters: Double,
    ): OsrmRouteResult {
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

        return lastResult
            ?: throw UnprocessableEntityException(
                ErrorCodes.DISCOVERY_NO_ROUTE,
                "요청하신 위치 주변에서 경로를 찾지 못했습니다.",
            )
    }

    /**
     * docs/02-api-spec.md 13.1/13.1.1/13.1.2/13.3 — mode: "SHAPE". Generates
     * [N_SHAPE_CANDIDATES] differently-rotated placements of [shapeType]'s normalized template
     * around [start] — each one's first point is always pinned to [start] itself (13.3: "시작
     * 좌표로 평행이동, 시작=끝 고정") — routes every candidate in order through [osrmShapeClient]
     * (13.2's dedicated arterial-sidewalk-preferring instance, never [osrmClient]), drops whichever
     * candidates fail to route, and hands the survivors to [selectBestShapeCandidate] to pick the
     * one whose routed geometry looks most like its own (rotated) template.
     *
     * [templateRadiusMeters] (from `size`, 13.1.1) fixes the scale directly — `scale =
     * templateRadiusMeters / template.maxRadiusMeters` — so unlike [generateLoopRoute] there is no
     * distance-target retry loop; every candidate uses the same scale, only the rotation differs.
     *
     * If every candidate's routing call fails, this throws the same `422 DISCOVERY_NO_ROUTE` as
     * before the redesign.
     */
    private suspend fun generateShapeRoute(
        shapeType: ShapeType,
        templateRadiusMeters: Double,
        start: LatLng,
    ): OsrmRouteResult {
        val template = shapeType.template
        val anchor = template.points.first()
        val scale = templateRadiusMeters / template.maxRadiusMeters

        val candidates = (1..N_SHAPE_CANDIDATES).mapNotNull {
            val rotationRadians = Random.nextDouble(0.0, 2 * PI)
            val templatePoints = template.points.map { point -> placeShapePoint(point, anchor, start, scale, rotationRadians) }
            val routeResult = runCatching { osrmShapeClient.route(templatePoints) }.getOrNull() ?: return@mapNotNull null
            ShapeRouteCandidate(templatePoints = templatePoints, routeResult = routeResult)
        }

        if (candidates.isEmpty()) {
            throw UnprocessableEntityException(
                ErrorCodes.DISCOVERY_NO_ROUTE,
                "요청하신 위치 주변에서 경로를 찾지 못했습니다.",
            )
        }

        return selectBestShapeCandidate(candidates).routeResult
    }

    /**
     * Maps one [ShapeTemplate] point to a real-world [LatLng]: its offset from [anchor] — in the
     * template's own tiny-degree space, which per [ShapeTemplate]'s doc comment is small enough
     * that degree-deltas there are indistinguishable from an undistorted local meters plane — is
     * converted to meters, rotated by [rotationRadians], multiplied by [scale], then projected from
     * [start] via [GeoMath.destination] (which correctly accounts for [start]'s actual latitude,
     * unlike a naive degree-delta addition would). [anchor] itself always maps back to exactly
     * [start] (zero offset) — that's what pins the generated loop's start and end together.
     */
    private fun placeShapePoint(
        point: LatLng,
        anchor: LatLng,
        start: LatLng,
        scale: Double,
        rotationRadians: Double,
    ): LatLng {
        val dxMeters = (point.lng - anchor.lng) * DEGREES_TO_METERS
        val dyMeters = (point.lat - anchor.lat) * DEGREES_TO_METERS
        if (dxMeters == 0.0 && dyMeters == 0.0) return start

        val cosR = cos(rotationRadians)
        val sinR = sin(rotationRadians)
        val rotatedX = (dxMeters * cosR - dyMeters * sinR) * scale
        val rotatedY = (dxMeters * sinR + dyMeters * cosR) * scale

        val distance = sqrt(rotatedX * rotatedX + rotatedY * rotatedY)
        if (distance == 0.0) return start

        val bearing = (Math.toDegrees(atan2(rotatedX, rotatedY)) + 360.0) % 360.0
        return GeoMath.destination(start, bearing, distance)
    }

    /**
     * docs/02-api-spec.md 11.4 — routes from [start] to a fixed [end], detouring only if the
     * direct route falls short of [targetDistanceMeters]. A too-long direct route (destination is
     * simply farther than the target) is returned as-is — a fixed endpoint can't be shortened.
     *
     * The detour point sits perpendicular to the start-end bearing at a [radius] from [start],
     * jittered/side-randomized so "다시 생성" gives a different bow each time; radius is adjusted
     * by the same over/undershoot ratio as [generateLoopRoute]'s candidates.
     */
    private suspend fun generatePointToPointRoute(
        start: LatLng,
        end: LatLng,
        targetDistanceMeters: Double,
    ): OsrmRouteResult {
        val direct = runCatching { osrmClient.route(listOf(start, end)) }.getOrNull()
            ?: throw UnprocessableEntityException(
                ErrorCodes.DISCOVERY_NO_ROUTE,
                "요청하신 두 지점 사이에서 경로를 찾지 못했습니다.",
            )

        val lowerBound = targetDistanceMeters * (1 - TARGET_TOLERANCE)
        if (direct.distanceMeters >= lowerBound) return direct

        return routeWithPerpendicularDetour(
            baseRoute = listOf(start, end),
            insertIndex = 1,
            detourOrigin = start,
            bearingFrom = start,
            bearingTo = end,
            targetDistanceMeters = targetDistanceMeters,
            fallback = direct,
        )
    }

    /**
     * docs/02-api-spec.md 11.6 — point-to-point routing combined with `requiredWaypoints`. Unlike
     * 11.2's loop mode, there's no K-slot bearing system to pin waypoints to, so instead each
     * waypoint is ordered by `(distance-to-start - distance-to-end)` ascending (closer to start
     * relative to end visited first), then the whole thing — `start -> waypoints -> end` — is
     * routed in a single OSRM call.
     *
     * If that combined route already meets [targetDistanceMeters] (or overshoots it), it's
     * returned as-is, same "can't shorten fixed points" policy as 11.4. If it falls short, exactly
     * one perpendicular detour point is added — inserted into whichever leg of the combined route
     * is straight-line (Haversine) longest, since that's the leg with the most slack to absorb a
     * detour without radically distorting the route.
     */
    private suspend fun generatePointToPointWithWaypointsRoute(
        start: LatLng,
        end: LatLng,
        requiredWaypoints: List<LatLng>,
        targetDistanceMeters: Double,
    ): OsrmRouteResult {
        val sortedWaypoints = requiredWaypoints.sortedBy { wp ->
            GeoMath.haversineMeters(start, wp) - GeoMath.haversineMeters(wp, end)
        }
        val baseRoute = listOf(start) + sortedWaypoints + end

        val direct = runCatching { osrmClient.route(baseRoute) }.getOrNull()
            ?: throw UnprocessableEntityException(
                ErrorCodes.DISCOVERY_NO_ROUTE,
                "요청하신 지점들 사이에서 경로를 찾지 못했습니다.",
            )

        val lowerBound = targetDistanceMeters * (1 - TARGET_TOLERANCE)
        if (direct.distanceMeters >= lowerBound) return direct

        val longestLegIndex = (0 until baseRoute.size - 1)
            .maxByOrNull { i -> GeoMath.haversineMeters(baseRoute[i], baseRoute[i + 1]) }!!

        return routeWithPerpendicularDetour(
            baseRoute = baseRoute,
            insertIndex = longestLegIndex + 1,
            detourOrigin = baseRoute[longestLegIndex],
            bearingFrom = baseRoute[longestLegIndex],
            bearingTo = baseRoute[longestLegIndex + 1],
            targetDistanceMeters = targetDistanceMeters,
            fallback = direct,
        )
    }

    /**
     * Shared by [generatePointToPointRoute] (11.4) and [generatePointToPointWithWaypointsRoute]
     * (11.6): inserts one perpendicular-bearing detour point — computed from [bearingFrom] to
     * [bearingTo], placed [radius] meters from [detourOrigin], side/jitter randomized — at
     * [insertIndex] in [baseRoute], then routes and retries up to [MAX_ATTEMPTS] times, adjusting
     * only the detour's radius by the over/undershoot ratio each time (same principle as
     * [generateLoopRoute]'s candidates). Falls back to [fallback] — the already-succeeded,
     * too-short pre-detour route — if every detour attempt throws.
     */
    private suspend fun routeWithPerpendicularDetour(
        baseRoute: List<LatLng>,
        insertIndex: Int,
        detourOrigin: LatLng,
        bearingFrom: LatLng,
        bearingTo: LatLng,
        targetDistanceMeters: Double,
        fallback: OsrmRouteResult,
    ): OsrmRouteResult {
        val lowerBound = targetDistanceMeters * (1 - TARGET_TOLERANCE)
        val upperBound = targetDistanceMeters * (1 + TARGET_TOLERANCE)

        val baseBearing = GeoMath.bearingDegrees(bearingFrom, bearingTo)
        val side = if (Random.nextBoolean()) 1.0 else -1.0
        val detourBearing = baseBearing + side * 90.0 + Random.nextDouble(-POINT_TO_POINT_BEARING_JITTER_DEGREES, POINT_TO_POINT_BEARING_JITTER_DEGREES)
        var radius = targetDistanceMeters / 2.0

        var lastResult = fallback

        for (attempt in 1..MAX_ATTEMPTS) {
            val detour = GeoMath.destination(detourOrigin, detourBearing, radius)
            val candidateRoute = baseRoute.toMutableList().apply { add(insertIndex, detour) }
            val result = runCatching { osrmClient.route(candidateRoute) }.getOrNull() ?: continue
            lastResult = result

            if (result.distanceMeters in lowerBound..upperBound) break
            if (result.distanceMeters > 0) {
                radius *= targetDistanceMeters / result.distanceMeters
            }
        }

        return lastResult
    }

    private fun respond(result: OsrmRouteResult): DiscoveryResponse {
        val distanceKm = result.distanceMeters.toRoundedKm()
        return DiscoveryResponse(
            geoJson = result.geometry,
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

// ---------------------------------------------------------------------------------------------
// docs/02-api-spec.md 13.1.2 — SHAPE candidate-selection, split out as free (top-level) functions
// so the "candidate list -> best candidate" decision is directly unit-testable with fake data,
// without needing an OsrmClient/DiscoveryService instance at all.
// ---------------------------------------------------------------------------------------------

/**
 * docs/02-api-spec.md 13.1.2 (redesign) — instead of one random-rotation attempt, generate this
 * many differently-rotated candidates, route each, and keep the one whose routed geometry is
 * closest (by Fréchet distance) to its own rotated template. A candidate whose routing call fails
 * is simply dropped, not retried — with 6 candidates there's no need to retry an individual
 * failure, only to fall through to DISCOVERY_NO_ROUTE if *every* candidate fails (see
 * [DiscoveryService.generateShapeRoute] — private, but this constant is `internal` so tests can
 * reference it directly instead of hardcoding 6).
 */
internal const val N_SHAPE_CANDIDATES = 6

/** docs/02-api-spec.md 13.1.2 — "동일 개수(예: 40개) 점으로 재샘플링" before scoring two shapes against each other. */
internal const val SHAPE_CANDIDATE_COMPARISON_POINTS = 40

/**
 * One routed SHAPE candidate (docs/02-api-spec.md 13.1.2): [templatePoints] is the
 * rotated/scaled/placed template waypoint list that was *sent* to OSRM (the "planned" shape),
 * [routeResult] is what OSRM actually returned for it (the "real road" shape). Kept as a pair so
 * [selectBestShapeCandidate] can compare the two without re-deriving either.
 */
internal data class ShapeRouteCandidate(
    val templatePoints: List<LatLng>,
    val routeResult: OsrmRouteResult,
)

/**
 * docs/02-api-spec.md 13.1.2 — picks whichever [candidates] entry's routed geometry looks most
 * like its own rotated template, by resampling both to [SHAPE_CANDIDATE_COMPARISON_POINTS] evenly
 * arc-length-spaced points ([GeoMath.resample]) and scoring the pair with
 * [FrechetDistance.discreteMeters] (the same "shape similarity" metric
 * `RunJudgementService` uses for the Sketch Match score) — smaller means "more similar," so the
 * minimum wins.
 *
 * Pure and OSRM-free: every input is already-fetched data, so a unit test can drive this directly
 * with hand-built "good" (template-shaped) vs. "mangled" (blob-shaped) route geometries and assert
 * the former wins, with no fake network client involved.
 */
internal fun selectBestShapeCandidate(candidates: List<ShapeRouteCandidate>): ShapeRouteCandidate {
    require(candidates.isNotEmpty()) { "selectBestShapeCandidate requires at least one candidate" }
    return candidates.minBy { candidate ->
        val plannedShape = GeoMath.resample(candidate.templatePoints, SHAPE_CANDIDATE_COMPARISON_POINTS)
        val actualShape = GeoMath.resample(candidate.routeResult.geometry.toLatLngList(), SHAPE_CANDIDATE_COMPARISON_POINTS)
        FrechetDistance.discreteMeters(plannedShape, actualShape)
    }
}
