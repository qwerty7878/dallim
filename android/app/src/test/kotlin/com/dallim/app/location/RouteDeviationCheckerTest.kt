package com.dallim.app.location

import com.dallim.ui.components.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-23 코스 이탈 안내 로직 유닛 테스트 — point-to-line 거리 계산과 60m/15초 트리거를
 * Android 프레임워크 없이 검증한다 (docs/01-feature-spec.md §1.3: "서버 왕복 없이 즉시 반응").
 */
class RouteDeviationCheckerTest {

    /** 서울시청 부근을 지나는 대략 동서 방향 직선 경로. */
    private val straightRoute = listOf(
        GeoPoint(lng = 126.9780, lat = 37.5665),
        GeoPoint(lng = 126.9880, lat = 37.5665),
    )

    @Test
    fun `point exactly on the route line has zero distance`() {
        val onRoute = GeoPoint(lng = 126.9830, lat = 37.5665)
        val distance = RouteDeviationChecker.distanceToRouteMeters(onRoute, straightRoute)
        assertTrue("expected near-zero distance but was $distance", distance < 1.0)
    }

    @Test
    fun `point far from the route line returns large distance`() {
        // ~0.001 deg lat north is roughly 111m away from the straight route.
        val farPoint = GeoPoint(lng = 126.9830, lat = 37.5675)
        val distance = RouteDeviationChecker.distanceToRouteMeters(farPoint, straightRoute)
        assertTrue("expected > 60m but was $distance", distance > 60.0)
    }

    @Test
    fun `fewer than two route points is treated as undefined and never triggers`() {
        val distance = RouteDeviationChecker.distanceToRouteMeters(
            GeoPoint(lng = 126.978, lat = 37.5665),
            listOf(GeoPoint(lng = 126.978, lat = 37.5665)),
        )
        assertEquals(Double.MAX_VALUE, distance, 0.0)
    }

    @Test
    fun `deviation tracker does not trigger before 15 seconds sustained`() {
        val tracker = RouteDeviationTracker(thresholdMeters = 60.0, sustainedMillis = 15_000L)
        val start = 0L
        assertFalse(tracker.onLocation(distanceMeters = 100.0, nowMillis = start))
        assertFalse(tracker.onLocation(distanceMeters = 100.0, nowMillis = start + 10_000L))
    }

    @Test
    fun `deviation tracker triggers once 15 seconds sustained off-route`() {
        val tracker = RouteDeviationTracker(thresholdMeters = 60.0, sustainedMillis = 15_000L)
        val start = 0L
        assertFalse(tracker.onLocation(distanceMeters = 100.0, nowMillis = start))
        assertTrue(tracker.onLocation(distanceMeters = 100.0, nowMillis = start + 15_000L))
    }

    @Test
    fun `returning under threshold resets the sustained timer`() {
        val tracker = RouteDeviationTracker(thresholdMeters = 60.0, sustainedMillis = 15_000L)
        val start = 0L
        tracker.onLocation(distanceMeters = 100.0, nowMillis = start)
        // Comes back on-route before 15s elapses.
        assertFalse(tracker.onLocation(distanceMeters = 10.0, nowMillis = start + 10_000L))
        // Drifts off again — timer must have restarted, so still no trigger at +5s from this point.
        assertFalse(tracker.onLocation(distanceMeters = 100.0, nowMillis = start + 15_000L))
    }
}
