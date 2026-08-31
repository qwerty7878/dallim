package com.dallim.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DouglasPeuckerTest {

    @Test
    fun `fewer than 3 points is returned unchanged`() {
        val one = listOf(LatLng(37.0, 127.0))
        val two = listOf(LatLng(37.0, 127.0), LatLng(37.001, 127.001))
        assertEquals(one, DouglasPeucker.simplify(one, epsilonMeters = 5.0))
        assertEquals(two, DouglasPeucker.simplify(two, epsilonMeters = 5.0))
    }

    @Test
    fun `collinear points simplify down to just the endpoints`() {
        val points = (0..10).map { LatLng(37.40, 127.0000 + it * 0.0001) }
        val simplified = DouglasPeucker.simplify(points, epsilonMeters = 1.0)

        assertEquals(listOf(points.first(), points.last()), simplified)
    }

    @Test
    fun `a point that deviates beyond epsilon is kept`() {
        val points = listOf(
            LatLng(37.40, 127.0000),
            LatLng(37.40, 127.0005),
            // ~50m north detour at the midpoint.
            LatLng(37.4045, 127.0010),
            LatLng(37.40, 127.0015),
            LatLng(37.40, 127.0020),
        )
        val simplified = DouglasPeucker.simplify(points, epsilonMeters = 5.0)

        assertTrue(simplified.contains(points[2]), "the detour point should survive simplification")
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
    }

    @Test
    fun `a small deviation within epsilon is dropped`() {
        val points = listOf(
            LatLng(37.40, 127.0000),
            LatLng(37.40, 127.0005),
            // ~1m deviation only.
            LatLng(37.400009, 127.0010),
            LatLng(37.40, 127.0015),
            LatLng(37.40, 127.0020),
        )
        val simplified = DouglasPeucker.simplify(points, epsilonMeters = 5.0)

        assertEquals(listOf(points.first(), points.last()), simplified)
    }
}
