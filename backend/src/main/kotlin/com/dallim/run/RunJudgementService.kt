package com.dallim.run

import com.dallim.common.DouglasPeucker
import com.dallim.common.FrechetDistance
import com.dallim.common.GeoMath
import com.dallim.common.GeoMath.toPercentInt
import com.dallim.common.LatLng
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * One recorded GPS fix used as judgement input — plain lat/lng/timestamp, deliberately
 * independent of the Exposed [GpsPoint] entity so this whole judgement algorithm stays a pure,
 * easily unit-testable function of its inputs (qa-engineer verifies it directly against
 * hand-built GPS sample data).
 */
data class TimedPoint(val lat: Double, val lng: Double, val timestamp: Instant) {
    fun toLatLng(): LatLng = LatLng(lat, lng)
}

/** Output of [RunJudgementService.judge] — everything RunService needs to persist + respond with. */
data class RunJudgementResult(
    val status: RunStatus,
    val distanceMeters: Double,
    val durationSeconds: Int,
    val routeCompletionPercent: Int,
    val sketchMatchPercent: Int,
    val hasAbnormalSpeed: Boolean,
    val abnormalSpeedRatio: Double,
    /** Douglas-Peucker-simplified actual track, ready to persist as RunRecord.actual_path. */
    val simplifiedActualPath: List<LatLng>,
)

/**
 * Server-side finish-judgement algorithm — the sole source of truth for a run's outcome
 * (CLAUDE.md rule 3, docs/01-feature-spec.md 2.2.D). A client-sent `clientPrecheckStatus` is
 * never consulted here; it is UX-preview-only and RunService must not forward it into this class.
 *
 * Every public method is a pure function of its arguments (no DB/Redis/clock access), so
 * qa-engineer can unit test each judgement step independently with synthetic GPS traces.
 */
class RunJudgementService {

    companion object {
        // --- Route Coverage (docs/01-feature-spec.md 2.2.D step 2) ---

        /** "각 세그먼트 반경 20m" — the exact figure SPEC pins down. */
        const val COVERAGE_RADIUS_METERS = 20.0

        // SPEC says "계획 LineString을 N개 세그먼트로 나누고" but does not pin N. One checkpoint
        // every ~15m is fine-grained enough to catch a runner skipping a real chunk of the route
        // while staying cheap to compute; clamped so very short or very long routes both get a
        // sane number of checkpoints.
        private const val METERS_PER_COVERAGE_SEGMENT = 15.0
        private const val MIN_COVERAGE_SEGMENTS = 10
        private const val MAX_COVERAGE_SEGMENTS = 200

        const val COMPLETED_COVERAGE_THRESHOLD = 90
        const val PARTIAL_COVERAGE_THRESHOLD = 50

        // --- Abnormal speed (docs/01-feature-spec.md 2.2.D step 3) ---

        /** "25km/h 초과" — the exact figure SPEC pins down. */
        const val ABNORMAL_SPEED_KMH = 25.0

        // SPEC says "비율 계산 -> 임계치 초과 시 UNDER_REVIEW" but does not pin the ratio
        // threshold. 5% is a deliberately conservative default so a couple of GPS-noise spikes
        // out of hundreds of fixes don't flip a genuine run to UNDER_REVIEW; adjust here if
        // qa-engineer's sample data calls for a different value.
        const val ABNORMAL_SPEED_RATIO_THRESHOLD = 0.05

        // --- Sketch Match (docs/01-feature-spec.md 2.2.D step 4) ---

        // SPEC calls for a 0~100 Fréchet-distance-based score but doesn't give the normalization
        // formula. 150m is a rough "the shapes no longer resemble each other" cutoff at typical
        // MVP1 sketch-route scale (few-km routes) — score falls off linearly from 0m to this.
        const val SKETCH_MATCH_ZERO_METERS = 150.0

        /** Points per sequence fed into the O(n*m) Fréchet computation are capped at this. */
        private const val FRECHET_MAX_POINTS = 300

        /** Below this many uploaded points, judgement can't run at all -> GPS_DATA_INSUFFICIENT. */
        const val MIN_POINTS_FOR_JUDGEMENT = 2

        /** Default Douglas-Peucker tolerance for the persisted actual_path simplification. */
        const val STORAGE_SIMPLIFICATION_EPSILON_METERS = 5.0
    }

    /**
     * Runs the full judgement pipeline against a planned route and a run's raw GPS fixes.
     *
     * @param planned the route's planned LineString, in order, at least 2 points.
     * @param actual the run's raw uploaded GPS fixes; re-sorted by timestamp internally so
     *   caller-side ordering/duplication in gps-batch uploads doesn't matter.
     */
    fun judge(planned: List<LatLng>, actual: List<TimedPoint>): RunJudgementResult {
        require(planned.size >= 2) { "planned route must have at least 2 points" }
        require(actual.size >= MIN_POINTS_FOR_JUDGEMENT) { "insufficient GPS points for judgement" }

        val orderedActual = actual.sortedBy { it.timestamp }
        val actualLatLngs = orderedActual.map { it.toLatLng() }

        val distanceMeters = GeoMath.pathLengthMeters(actualLatLngs)
        val durationSeconds = Duration.between(orderedActual.first().timestamp, orderedActual.last().timestamp)
            .seconds
            .coerceAtLeast(0)
            .toInt()

        val coveragePercent = routeCoveragePercent(planned, actualLatLngs)
        val abnormalRatio = abnormalSpeedRatio(orderedActual)
        val hasAbnormalSpeed = abnormalRatio > ABNORMAL_SPEED_RATIO_THRESHOLD
        val sketchMatch = sketchMatchPercent(planned, actualLatLngs)

        // docs/01-feature-spec.md 2.2.D final table: abnormal speed unconditionally forces
        // UNDER_REVIEW (COMPLETED explicitly requires "AND 비정상속도 없음"); otherwise the
        // coverage bands decide.
        val status = when {
            hasAbnormalSpeed -> RunStatus.UNDER_REVIEW
            coveragePercent >= COMPLETED_COVERAGE_THRESHOLD -> RunStatus.COMPLETED
            coveragePercent >= PARTIAL_COVERAGE_THRESHOLD -> RunStatus.PARTIAL
            else -> RunStatus.ABORTED
        }

        return RunJudgementResult(
            status = status,
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            routeCompletionPercent = coveragePercent.toPercentInt(),
            sketchMatchPercent = sketchMatch,
            hasAbnormalSpeed = hasAbnormalSpeed,
            abnormalSpeedRatio = abnormalRatio,
            simplifiedActualPath = simplifyForStorage(actualLatLngs),
        )
    }

    /**
     * Route Coverage (docs/01-feature-spec.md 2.2.D step 2): re-samples [planned] into evenly
     * spaced checkpoints and reports the percentage of checkpoints that have at least one
     * [actual] point within [COVERAGE_RADIUS_METERS].
     */
    fun routeCoveragePercent(planned: List<LatLng>, actual: List<LatLng>): Double {
        if (actual.isEmpty()) return 0.0

        val plannedLengthMeters = GeoMath.pathLengthMeters(planned)
        val segmentCount = (plannedLengthMeters / METERS_PER_COVERAGE_SEGMENT).roundToInt()
            .coerceIn(MIN_COVERAGE_SEGMENTS, MAX_COVERAGE_SEGMENTS)
        val checkpoints = GeoMath.resample(planned, segmentCount + 1)

        val coveredCount = checkpoints.count { checkpoint ->
            actual.any { GeoMath.haversineMeters(checkpoint, it) <= COVERAGE_RADIUS_METERS }
        }
        return coveredCount * 100.0 / checkpoints.size
    }

    /**
     * Abnormal-speed ratio (docs/01-feature-spec.md 2.2.D step 3): fraction of consecutive-point
     * segments whose implied speed exceeds [ABNORMAL_SPEED_KMH]. Pairs with a non-positive time
     * delta (duplicate/out-of-order timestamps) are skipped rather than treated as infinite speed.
     */
    fun abnormalSpeedRatio(points: List<TimedPoint>): Double {
        if (points.size < 2) return 0.0

        var abnormalCount = 0
        var totalSamples = 0
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val seconds = Duration.between(prev.timestamp, curr.timestamp).seconds
            if (seconds <= 0) continue

            val meters = GeoMath.haversineMeters(prev.toLatLng(), curr.toLatLng())
            val kmh = (meters / seconds) * 3.6
            totalSamples++
            if (kmh > ABNORMAL_SPEED_KMH) abnormalCount++
        }
        return if (totalSamples == 0) 0.0 else abnormalCount.toDouble() / totalSamples
    }

    /**
     * Sketch Match (docs/01-feature-spec.md 2.2.D step 4): 0~100 similarity score derived from
     * the discrete Fréchet distance between the planned and actual polylines. Both sequences are
     * capped to [FRECHET_MAX_POINTS] via [GeoMath.resample] first (shape-preserving) to bound the
     * O(n*m) cost when a run has thousands of raw fixes.
     */
    fun sketchMatchPercent(planned: List<LatLng>, actual: List<LatLng>): Int {
        if (actual.isEmpty()) return 0

        val sampledPlanned = if (planned.size > FRECHET_MAX_POINTS) GeoMath.resample(planned, FRECHET_MAX_POINTS) else planned
        val sampledActual = if (actual.size > FRECHET_MAX_POINTS) GeoMath.resample(actual, FRECHET_MAX_POINTS) else actual

        val frechetMeters = FrechetDistance.discreteMeters(sampledPlanned, sampledActual)
        val score = 100.0 * (1.0 - (frechetMeters / SKETCH_MATCH_ZERO_METERS)).coerceIn(0.0, 1.0)
        return score.roundToInt()
    }

    /**
     * Douglas-Peucker-simplifies the actual track for storage as RunRecord.actual_path
     * (docs/01-feature-spec.md 2.4) — the raw fixes stay in gps_points regardless.
     */
    fun simplifyForStorage(
        actual: List<LatLng>,
        epsilonMeters: Double = STORAGE_SIMPLIFICATION_EPSILON_METERS,
    ): List<LatLng> = DouglasPeucker.simplify(actual, epsilonMeters)

    /** Shared distance/duration -> pace formula, used both right after [judge] and when
     * re-deriving a persisted run's pace for GET /runs/{runId}. Zero distance -> 0 (not NaN/Inf). */
    fun averagePaceSecPerKm(distanceMeters: Double, durationSeconds: Int): Int {
        val distanceKm = distanceMeters / 1000.0
        if (distanceKm <= 0.0) return 0
        return (durationSeconds / distanceKm).roundToInt()
    }
}
