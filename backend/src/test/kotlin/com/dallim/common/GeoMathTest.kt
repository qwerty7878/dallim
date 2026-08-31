package com.dallim.common

import com.dallim.common.GeoMath.toPercentInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GeoMath underpins every RunJudgementService calculation (distance/coverage/resampling), so its
 * correctness is tested directly and independently of the judgement pipeline.
 */
class GeoMathTest {

    @Test
    fun `haversineMeters - identical point is zero distance`() {
        val p = LatLng(37.3905, 126.9235)
        assertEquals(0.0, GeoMath.haversineMeters(p, p), 1e-6)
    }

    @Test
    fun `haversineMeters - one degree of latitude is about 111 km on a spherical earth`() {
        // GeoMath uses a spherical-earth radius of 6,371,000m, so 1 deg latitude = R * (pi/180).
        val distance = GeoMath.haversineMeters(LatLng(0.0, 0.0), LatLng(1.0, 0.0))
        assertEquals(111_194.9, distance, 50.0)
    }

    @Test
    fun `pathLengthMeters - sums consecutive segment distances`() {
        val points = listOf(LatLng(37.0, 127.0), LatLng(37.001, 127.0), LatLng(37.001, 127.001))
        val total = GeoMath.pathLengthMeters(points)
        val expected = GeoMath.haversineMeters(points[0], points[1]) + GeoMath.haversineMeters(points[1], points[2])
        assertEquals(expected, total, 1e-6)
    }

    @Test
    fun `pathLengthMeters - fewer than 2 points is zero`() {
        assertEquals(0.0, GeoMath.pathLengthMeters(emptyList()))
        assertEquals(0.0, GeoMath.pathLengthMeters(listOf(LatLng(0.0, 0.0))))
    }

    @Test
    fun `resample - straight 2-point line, midpoint of a 3-point resample is the geometric midpoint`() {
        val a = LatLng(37.0, 127.0)
        val b = LatLng(37.0, 127.01)
        val resampled = GeoMath.resample(listOf(a, b), 3)

        assertEquals(3, resampled.size)
        assertEquals(a.lat, resampled.first().lat, 1e-9)
        assertEquals(b.lat, resampled.last().lat, 1e-9)
        assertEquals((a.lng + b.lng) / 2, resampled[1].lng, 1e-9)
    }

    @Test
    fun `resample - degenerate zero-length path returns the same point repeated`() {
        val p = LatLng(37.0, 127.0)
        val resampled = GeoMath.resample(listOf(p, p), 4)
        assertTrue(resampled.all { it == p })
    }

    @Test
    fun `toPercentInt - rounds and clamps to the 0 to 100 range`() {
        assertEquals(90, 89.6.toPercentInt())
        assertEquals(100, 150.0.toPercentInt())
        assertEquals(0, (-5.0).toPercentInt())
    }
}
