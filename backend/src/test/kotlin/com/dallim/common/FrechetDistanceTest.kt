package com.dallim.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrechetDistanceTest {

    @Test
    fun `identical sequences have zero Frechet distance`() {
        val line = listOf(LatLng(37.0, 127.0), LatLng(37.001, 127.001), LatLng(37.002, 127.002))
        assertEquals(0.0, FrechetDistance.discreteMeters(line, line), 1e-6)
    }

    @Test
    fun `a line offset by a constant lng shift has Frechet distance close to that shift's meters`() {
        val p = listOf(LatLng(37.40, 127.0000), LatLng(37.40, 127.0010), LatLng(37.40, 127.0020))
        // Same latitude, shifted east by a small constant lng delta.
        val q = listOf(LatLng(37.40, 127.0001), LatLng(37.40, 127.0011), LatLng(37.40, 127.0021))

        val expectedShiftMeters = GeoMath.haversineMeters(p[0], q[0])
        val distance = FrechetDistance.discreteMeters(p, q)

        assertEquals(expectedShiftMeters, distance, 0.5)
    }

    @Test
    fun `empty sequence yields MAX_VALUE rather than throwing`() {
        val line = listOf(LatLng(37.0, 127.0), LatLng(37.001, 127.0))
        assertEquals(Double.MAX_VALUE, FrechetDistance.discreteMeters(emptyList(), line))
        assertEquals(Double.MAX_VALUE, FrechetDistance.discreteMeters(line, emptyList()))
    }

    @Test
    fun `a large detour in the middle dominates the discrete Frechet distance`() {
        val planned = listOf(LatLng(37.40, 127.0000), LatLng(37.40, 127.0010), LatLng(37.40, 127.0020))
        // Same start/end, but a ~500m northward detour in the middle point.
        val detoured = listOf(LatLng(37.40, 127.0000), LatLng(37.4045, 127.0010), LatLng(37.40, 127.0020))

        val distance = FrechetDistance.discreteMeters(planned, detoured)
        assertTrue(distance > 400.0, "expected the detour to dominate the leash length, was $distance")
    }
}
