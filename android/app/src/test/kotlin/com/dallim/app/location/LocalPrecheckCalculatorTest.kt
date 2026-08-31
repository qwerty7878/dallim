package com.dallim.app.location

import com.dallim.ui.components.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-24/§1.3 로컬 완주 판정 프리체크 유닛 테스트. 이 계산 결과는 UX 프리뷰 전용이며 최종 판정은
 * 항상 서버 응답으로 덮어쓴다 — 여기서는 그 프리뷰 로직 자체의 정확성만 검증한다.
 */
class LocalPrecheckCalculatorTest {

    @Test
    fun `full coverage of the planned route yields COMPLETED status`() {
        val planned = listOf(
            GeoPoint(lng = 126.9780, lat = 37.5665),
            GeoPoint(lng = 126.9790, lat = 37.5665),
            GeoPoint(lng = 126.9800, lat = 37.5665),
        )
        val now = 0L
        // 60s/leg at ~88m/leg is a ~5km/h jog pace — comfortably under the 25km/h abnormal-speed gate.
        val actual = planned.mapIndexed { i, p -> TimedGeoPoint(p, now + i * 60_000L) }

        val result = LocalPrecheckCalculator.calculate(actual, planned)

        assertEquals(100, result.coveragePercent)
        assertFalse(result.hasAbnormalSpeed)
        assertEquals("COMPLETED", result.status)
    }

    @Test
    fun `no overlap with the planned route yields ABORTED status`() {
        val planned = listOf(
            GeoPoint(lng = 126.9780, lat = 37.5665),
            GeoPoint(lng = 126.9800, lat = 37.5665),
        )
        // Actual points are ~1km away from the planned route -> zero coverage. 10 minutes apart
        // keeps the pace realistic (~5km/h) so the abnormal-speed check doesn't also fire here.
        val actual = listOf(
            TimedGeoPoint(GeoPoint(lng = 127.0, lat = 37.6), 0L),
            TimedGeoPoint(GeoPoint(lng = 127.01, lat = 37.6), 600_000L),
        )

        val result = LocalPrecheckCalculator.calculate(actual, planned)

        assertEquals(0, result.coveragePercent)
        assertEquals("ABORTED", result.status)
    }

    @Test
    fun `speed over 25kmh sustained flags UNDER_REVIEW regardless of coverage`() {
        val planned = listOf(
            GeoPoint(lng = 126.9780, lat = 37.5665),
            GeoPoint(lng = 126.9790, lat = 37.5665),
        )
        // ~780m in 10 seconds is ~280km/h — clearly abnormal.
        val actual = listOf(
            TimedGeoPoint(GeoPoint(lng = 126.9780, lat = 37.5665), 0L),
            TimedGeoPoint(GeoPoint(lng = 126.9790, lat = 37.5665), 10_000L),
        )

        val result = LocalPrecheckCalculator.calculate(actual, planned)

        assertTrue(result.hasAbnormalSpeed)
        assertEquals("UNDER_REVIEW", result.status)
    }

    @Test
    fun `total distance sums consecutive haversine legs`() {
        val points = listOf(
            GeoPoint(lng = 126.9780, lat = 37.5665),
            GeoPoint(lng = 126.9790, lat = 37.5665),
            GeoPoint(lng = 126.9800, lat = 37.5665),
        )
        val distance = LocalPrecheckCalculator.totalDistanceMeters(points)
        // Roughly 2 legs of ~88m each at this latitude for 0.001deg lng.
        assertTrue("expected ~150-250m but was $distance", distance in 100.0..300.0)
    }
}
