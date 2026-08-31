package com.dallim.run

import com.dallim.common.GeoMath
import com.dallim.common.LatLng
import com.dallim.testsupport.GpsFixtures
import com.dallim.testsupport.actualTimedPoints
import com.dallim.testsupport.plannedLatLngs
import java.time.Instant
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [RunJudgementService] — the sole source of truth for a run's final status
 * (CLAUDE.md rule 3). Every function under test is a pure function of its arguments, so these
 * run with no DB/Redis/Ktor dependency at all.
 *
 * Two layers of coverage:
 *  1. Hand-built GPS fixtures (`src/test/resources/gps-fixtures/`, real Seoul/Gyeonggi-ish
 *     coordinates mirroring rt_001) exercised through the full [RunJudgementService.judge]
 *     pipeline — one per docs/01-feature-spec.md 2.2.D outcome.
 *  2. Synthetic straight-line inputs against the individual pure functions
 *     (routeCoveragePercent / abnormalSpeedRatio) to pin exact threshold-boundary behavior that
 *     would be fragile/hard to hit precisely with realistic multi-point fixtures.
 *
 * NOTE for future tuning: RunJudgementService.Companion documents several thresholds SPEC leaves
 * unpinned (segment density = 1 checkpoint/15m, ABNORMAL_SPEED_RATIO_THRESHOLD = 5%,
 * SKETCH_MATCH_ZERO_METERS = 150m). These tests lock in *current* behavior, not SPEC-mandated
 * values — see docs/qa-checklist.md for the "needs real-data tuning" note.
 */
class RunJudgementServiceTest {

    private val service = RunJudgementService()

    // ---------------------------------------------------------------------
    // 1. Fixture-based end-to-end scenarios
    // ---------------------------------------------------------------------

    @Test
    fun `completed fixture - close full-length trace yields COMPLETED with high coverage and no abnormal speed`() {
        val fixture = GpsFixtures.load("completed_run")

        val result = service.judge(fixture.plannedLatLngs(), fixture.actualTimedPoints())

        assertEquals(RunStatus.COMPLETED, result.status)
        assertTrue(result.routeCompletionPercent >= 90, "expected >=90% coverage, was ${result.routeCompletionPercent}")
        assertFalse(result.hasAbnormalSpeed)
        assertTrue(result.distanceMeters > 0)
        assertTrue(
            result.sketchMatchPercent >= 90,
            "dead-on-course trace should score high once planned/actual density is matched (see " +
                "sketchMatchPercent tests below), was ${result.sketchMatchPercent}",
        )
    }

    @Test
    fun `partial fixture - route followed for ~62pct then a shortcut yields PARTIAL with 50-90pct coverage`() {
        val fixture = GpsFixtures.load("partial_run")

        val result = service.judge(fixture.plannedLatLngs(), fixture.actualTimedPoints())

        assertEquals(RunStatus.PARTIAL, result.status)
        assertTrue(
            result.routeCompletionPercent in 50..89,
            "expected 50-89% coverage for a PARTIAL run, was ${result.routeCompletionPercent}",
        )
        assertFalse(result.hasAbnormalSpeed)
    }

    @Test
    fun `aborted fixture - route abandoned after ~15pct yields ABORTED with under-50pct coverage`() {
        val fixture = GpsFixtures.load("aborted_run")

        val result = service.judge(fixture.plannedLatLngs(), fixture.actualTimedPoints())

        assertEquals(RunStatus.ABORTED, result.status)
        assertTrue(result.routeCompletionPercent < 50, "expected <50% coverage, was ${result.routeCompletionPercent}")
    }

    @Test
    fun `under_review fixture - vehicle-speed teleports force UNDER_REVIEW even with full coverage`() {
        val fixture = GpsFixtures.load("under_review_run")

        val result = service.judge(fixture.plannedLatLngs(), fixture.actualTimedPoints())

        // Regression guard for docs/01-feature-spec.md 2.2.D: "COMPLETED requires coverage>=90%
        // AND no abnormal speed" -- abnormal speed must win even when coverage alone would pass.
        assertEquals(RunStatus.UNDER_REVIEW, result.status)
        assertTrue(result.hasAbnormalSpeed)
        assertTrue(
            result.routeCompletionPercent >= 90,
            "this fixture is deliberately built with high coverage to prove speed overrides it, was ${result.routeCompletionPercent}",
        )
        assertTrue(result.abnormalSpeedRatio > RunJudgementService.ABNORMAL_SPEED_RATIO_THRESHOLD)
    }

    // ---------------------------------------------------------------------
    // 2. Boundary tests on routeCoveragePercent (exact 90% / 50% thresholds)
    // ---------------------------------------------------------------------

    /**
     * Builds a straight 2-point planned line ~285m long at a fixed latitude and a dense actual
     * trace covering exactly [0, coveredMeters] of it. At this length, routeCoveragePercent's
     * internal segmentCount formula (round(285/15) = 19, clamped [10,200]) yields exactly 20
     * evenly-spaced checkpoints (~15m apart) -- so covered-checkpoint counts land on clean
     * multiples of 5%, letting us hit an exact threshold value rather than an approximation.
     */
    private fun straightLinePlanned(lengthMeters: Double = 285.0, latitude: Double = 37.40): List<LatLng> {
        val metersPerDegLng = 111_320.0 * cos(Math.toRadians(latitude))
        val lngDelta = lengthMeters / metersPerDegLng
        return listOf(LatLng(latitude, 127.0000), LatLng(latitude, 127.0000 + lngDelta))
    }

    private fun denseTraceAlong(planned: List<LatLng>, coveredMeters: Double, stepMeters: Double = 2.0): List<LatLng> {
        val start = planned.first()
        val end = planned.last()
        val totalLength = GeoMath.pathLengthMeters(planned)
        val fractionCovered = (coveredMeters / totalLength).coerceIn(0.0, 1.0)
        val steps = (coveredMeters / stepMeters).toInt().coerceAtLeast(1)
        return (0..steps).map { i ->
            val t = fractionCovered * i / steps
            LatLng(
                lat = start.lat + (end.lat - start.lat) * t,
                lng = start.lng + (end.lng - start.lng) * t,
            )
        }
    }

    @Test
    fun `routeCoveragePercent - exactly 90pct at the COMPLETED boundary`() {
        val planned = straightLinePlanned()
        // Chosen with a wide safety margin (see class-doc derivation): any actual trace ending
        // between ~235m and ~250m along this 285m/20-checkpoint line covers exactly 18/20
        // checkpoints (90%) -- 240m sits comfortably in the middle of that window.
        val actual = denseTraceAlong(planned, coveredMeters = 240.0)

        val coverage = service.routeCoveragePercent(planned, actual)

        assertEquals(90.0, coverage)
    }

    @Test
    fun `routeCoveragePercent - exactly 50pct at the PARTIAL-vs-ABORTED boundary`() {
        val planned = straightLinePlanned()
        // Same derivation, targeting 10/20 covered checkpoints: the safe window is ~[115m,130m).
        val actual = denseTraceAlong(planned, coveredMeters = 122.0)

        val coverage = service.routeCoveragePercent(planned, actual)

        assertEquals(50.0, coverage)
    }

    @Test
    fun `judge - exactly 90pct coverage with no abnormal speed resolves to COMPLETED`() {
        val planned = straightLinePlanned()
        val actualLatLngs = denseTraceAlong(planned, coveredMeters = 240.0)
        val actual = toTimedPoints(actualLatLngs, speedKmh = 10.0)

        val result = service.judge(planned, actual)

        assertEquals(90, result.routeCompletionPercent)
        assertEquals(RunStatus.COMPLETED, result.status)
    }

    @Test
    fun `judge - just under 90pct coverage resolves to PARTIAL, not COMPLETED`() {
        val planned = straightLinePlanned()
        // 220m lands in the 17/20=85% window (safe margin below the [235,250) 90% window).
        val actualLatLngs = denseTraceAlong(planned, coveredMeters = 220.0)
        val actual = toTimedPoints(actualLatLngs, speedKmh = 10.0)

        val result = service.judge(planned, actual)

        assertTrue(result.routeCompletionPercent < 90)
        assertEquals(RunStatus.PARTIAL, result.status)
    }

    @Test
    fun `judge - exactly 50pct coverage resolves to PARTIAL (lower bound is inclusive)`() {
        val planned = straightLinePlanned()
        val actualLatLngs = denseTraceAlong(planned, coveredMeters = 122.0)
        val actual = toTimedPoints(actualLatLngs, speedKmh = 10.0)

        val result = service.judge(planned, actual)

        assertEquals(50, result.routeCompletionPercent)
        assertEquals(RunStatus.PARTIAL, result.status)
    }

    @Test
    fun `judge - just under 50pct coverage resolves to ABORTED`() {
        val planned = straightLinePlanned()
        // 100m lands at 9/20=45% coverage (safe margin below the [115,130) 50% window).
        val actualLatLngs = denseTraceAlong(planned, coveredMeters = 100.0)
        val actual = toTimedPoints(actualLatLngs, speedKmh = 10.0)

        val result = service.judge(planned, actual)

        assertTrue(result.routeCompletionPercent < 50)
        assertEquals(RunStatus.ABORTED, result.status)
    }

    // ---------------------------------------------------------------------
    // 3. Abnormal-speed ratio boundary + override behavior
    // ---------------------------------------------------------------------

    @Test
    fun `abnormalSpeedRatio - exactly at the 5pct threshold does NOT trigger (strictly-greater-than semantics)`() {
        // 20 samples (21 points), exactly 1 abnormal (40km/h) -> ratio == 0.05 exactly.
        //
        // distanceM is deliberately 15m (not e.g. 10m): abnormalSpeedRatio computes elapsed time
        // via `Duration.between(...).seconds`, which truncates to whole seconds and discards the
        // sub-second remainder. At 10m/leg, both the 40km/h and 10km/h legs round down to a
        // *sub-1-second* raw duration, which truncates to 0 and gets silently skipped
        // (`if (seconds <= 0) continue`) -- silently dropping the pair from both the abnormal
        // count AND the total-sample denominator instead of registering it. 15m/leg keeps every
        // leg's raw duration >= 1s so it actually gets counted. This truncation-to-whole-seconds
        // is itself worth flagging to backend-dev (see docs/qa-checklist.md): any GPS batch with
        // sub-second-interval fixes will have those pairs excluded from anomaly detection
        // entirely rather than contributing a (correctly fractional) speed sample.
        val base = Instant.parse("2026-08-30T00:00:00Z")
        val points = mutableListOf(TimedPoint(37.40, 127.0000, base))
        var t = base
        repeat(20) { i ->
            val isAbnormalLeg = i == 0
            val speedKmh = if (isAbnormalLeg) 40.0 else 10.0
            val distanceM = 15.0
            val seconds = (distanceM / (speedKmh / 3.6))
            t = t.plusMillis((seconds * 1000).toLong())
            val prev = points.last()
            val metersPerDegLng = 111_320.0 * cos(Math.toRadians(prev.lat))
            points.add(TimedPoint(prev.lat, prev.lng + distanceM / metersPerDegLng, t))
        }

        val ratio = service.abnormalSpeedRatio(points)
        assertEquals(0.05, ratio, 1e-9)

        val planned = straightLinePlanned(lengthMeters = 250.0)
        val result = service.judge(planned, points)
        assertFalse(result.hasAbnormalSpeed, "ratio exactly at threshold must not flip hasAbnormalSpeed (spec: ratio > threshold)")
    }

    @Test
    fun `abnormalSpeedRatio - just above the 5pct threshold triggers UNDER_REVIEW regardless of coverage`() {
        // Reuse the COMPLETED fixture's near-perfect coverage trace, but rewrite a couple of its
        // legs to be car-speed jumps -- proves the override wins even against a run that would
        // otherwise clearly be COMPLETED.
        val fixture = GpsFixtures.load("completed_run")
        val original = fixture.actualTimedPoints()
        val mutated = original.toMutableList()
        // Collapse ~15 evenly-spread points' timestamps down to a 1-second gap from their
        // predecessor, turning each into a >25km/h jump. With ~186 speed-samples total this
        // yields a ratio of ~8% -- comfortably over the 5% cutoff with margin for fixture drift
        // (a single jump would only be ~0.5%, nowhere near enough to trigger the override).
        val jumpIndices = (10 until mutated.size - 5 step 12).toList()
        for (idx in jumpIndices) {
            val prev = mutated[idx - 1]
            val curr = mutated[idx]
            mutated[idx] = curr.copy(timestamp = prev.timestamp.plusSeconds(1))
        }

        val ratio = service.abnormalSpeedRatio(mutated)
        assertTrue(ratio > RunJudgementService.ABNORMAL_SPEED_RATIO_THRESHOLD, "ratio was $ratio")

        val result = service.judge(fixture.plannedLatLngs(), mutated)
        assertEquals(RunStatus.UNDER_REVIEW, result.status)
        assertTrue(result.routeCompletionPercent >= 90, "coverage should remain high -- speed alone must drive the override")
    }

    @Test
    fun `abnormalSpeedRatio - millisecond precision counts sub-1-second GPS pairs instead of dropping them`() {
        // Regression test for docs/qa-checklist.md round 1, bug #2: `Duration.between(...).seconds`
        // truncates to whole seconds, so a pair of points less than 1 second apart used to compute
        // `seconds == 0` and get silently skipped by `if (seconds <= 0) continue` -- vanishing from
        // both the abnormal-speed numerator AND the total-sample denominator instead of
        // contributing a correctly fractional speed sample.
        val base = Instant.parse("2026-08-30T00:00:00Z")
        val metersPerDegLng = 111_320.0 * cos(Math.toRadians(37.40))

        // Leg 1: 5m in 0.3s -> 60km/h (abnormal). Pre-fix, Duration.between(...).seconds == 0 for
        // this leg, so it used to be dropped from both numerator and denominator entirely.
        val p0 = TimedPoint(37.40, 127.0000, base)
        val p1 = TimedPoint(37.40, 127.0000 + 5.0 / metersPerDegLng, base.plusMillis(300))
        // Leg 2: 5m in 1.0s -> 18km/h (normal) -- counted identically before and after the fix.
        val p2 = TimedPoint(37.40, 127.0000 + 10.0 / metersPerDegLng, p1.timestamp.plusMillis(1000))

        val ratio = service.abnormalSpeedRatio(listOf(p0, p1, p2))

        // With millisecond precision: 1 abnormal leg out of 2 total legs -> ratio == 0.5. Before
        // the fix this evaluated to 0.0 (0 abnormal out of 1 total sample -- leg 1 vanished).
        assertEquals(0.5, ratio, 1e-9)

        val planned = straightLinePlanned(lengthMeters = 15.0)
        val result = service.judge(planned, listOf(p0, p1, p2))
        assertTrue(result.hasAbnormalSpeed, "the sub-1-second abnormal leg must now flip hasAbnormalSpeed")
    }

    // ---------------------------------------------------------------------
    // 4. Insufficient GPS data
    // ---------------------------------------------------------------------

    @Test
    fun `judge - throws for zero GPS points (GPS_DATA_INSUFFICIENT territory)`() {
        val planned = straightLinePlanned()
        assertFailsWith<IllegalArgumentException> {
            service.judge(planned, emptyList())
        }
    }

    @Test
    fun `judge - throws for a single GPS point, below MIN_POINTS_FOR_JUDGEMENT`() {
        val planned = straightLinePlanned()
        val onePoint = listOf(TimedPoint(37.40, 127.0000, Instant.parse("2026-08-30T00:00:00Z")))
        assertFailsWith<IllegalArgumentException> {
            service.judge(planned, onePoint)
        }
    }

    @Test
    fun `judge - exactly MIN_POINTS_FOR_JUDGEMENT points does not throw`() {
        assertEquals(2, RunJudgementService.MIN_POINTS_FOR_JUDGEMENT, "test assumes the documented minimum is 2")
        val planned = straightLinePlanned()
        val base = Instant.parse("2026-08-30T00:00:00Z")
        val twoPoints = listOf(
            TimedPoint(37.40, 127.0000, base),
            TimedPoint(37.40, 127.0010, base.plusSeconds(30)),
        )

        val result = service.judge(planned, twoPoints)

        // Not asserting a particular status -- just that judgement runs to completion at all.
        assertTrue(result.routeCompletionPercent in 0..100)
    }

    @Test
    fun `judge - throws for a planned route with fewer than 2 points`() {
        val actual = listOf(
            TimedPoint(37.40, 127.0000, Instant.parse("2026-08-30T00:00:00Z")),
            TimedPoint(37.40, 127.0010, Instant.parse("2026-08-30T00:00:30Z")),
        )
        assertFailsWith<IllegalArgumentException> {
            service.judge(listOf(LatLng(37.40, 127.0)), actual)
        }
    }

    // ---------------------------------------------------------------------
    // 5. sketchMatchPercent -- density-mismatch fix (docs/qa-checklist.md round 1, bug #1)
    // ---------------------------------------------------------------------

    /**
     * Regression test for a fixed bug (docs/qa-checklist.md round 1, bug #1): discrete Fréchet
     * distance is a *vertex-correspondence* metric, not a point-to-segment one, so feeding it a
     * sparse planned route (curated routes have only 5 vertices, 300-400m apart) directly
     * against a dense actual GPS trace (hundreds of points) used to inflate the distance to
     * ~192m purely from the density mismatch -- even for the `completed_run` fixture, a trace
     * built to hug rt_001's planned path within ~0m at every point. `sketchMatchPercent` now
     * resamples whichever of {planned, actual} is sparser up to the other's point count (linear
     * interpolation along arc length, via [GeoMath.resample]) before calling
     * [com.dallim.common.FrechetDistance.discreteMeters], which brings that same fixture's
     * distance down to ~3.6m and its score up to 98.
     */
    @Test
    fun `sketchMatchPercent - a dead-on-course trace scores near 100 despite a sparse planned route`() {
        val fixture = GpsFixtures.load("completed_run")

        val score = service.sketchMatchPercent(fixture.plannedLatLngs(), fixture.actualTimedPoints().map { it.toLatLng() })

        assertTrue(score >= 90, "expected a near-perfect similarity score for a dead-on-course trace, was $score")
    }

    @Test
    fun `sketchMatchPercent - pre-resampling the planned route to actual's density gives the same high score`() {
        // Companion to the test above: pre-resampling planned to actual's point count before
        // calling sketchMatchPercent should land on essentially the same score as letting
        // sketchMatchPercent do that resampling internally -- confirming the fix resamples
        // rather than relying on some other, narrower special-casing.
        val fixture = GpsFixtures.load("completed_run")
        val actual = fixture.actualTimedPoints().map { it.toLatLng() }
        val plannedResampled = GeoMath.resample(fixture.plannedLatLngs(), actual.size)

        val score = service.sketchMatchPercent(plannedResampled, actual)

        assertTrue(score >= 90, "expected a near-perfect similarity score once sampling density is matched, was $score")
    }

    // ---------------------------------------------------------------------
    // 6. averagePaceSecPerKm helper
    // ---------------------------------------------------------------------

    @Test
    fun `averagePaceSecPerKm - zero distance returns 0 instead of NaN or Infinity`() {
        assertEquals(0, service.averagePaceSecPerKm(distanceMeters = 0.0, durationSeconds = 600))
    }

    @Test
    fun `averagePaceSecPerKm - 5km in 25 minutes is 300 sec per km`() {
        assertEquals(300, service.averagePaceSecPerKm(distanceMeters = 5000.0, durationSeconds = 1500))
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private fun toTimedPoints(points: List<LatLng>, speedKmh: Double): List<TimedPoint> {
        val speedMs = speedKmh / 3.6
        var t = Instant.parse("2026-08-30T00:00:00Z")
        return points.mapIndexed { i, p ->
            if (i > 0) {
                val d = GeoMath.haversineMeters(points[i - 1], p)
                val seconds = if (speedMs <= 0.0) 1.0 else d / speedMs
                t = t.plusMillis((seconds * 1000).toLong().coerceAtLeast(1))
            }
            TimedPoint(p.lat, p.lng, t)
        }
    }
}
