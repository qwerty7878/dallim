package com.dallim.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ShapeType]/[ShapeTemplate] (docs/02-api-spec.md 13.3) — SVG path parsing,
 * single-subpath rejection, and the resample/normalization pipeline. No OSRM/network involved;
 * DiscoveryService's use of these templates (rotation/scale/routing) is covered separately in
 * DiscoveryServiceTest.
 */
class ShapeTemplatesTest {

    @Test
    fun `every registered ShapeType parses to exactly POINT_COUNT points`() {
        for (shapeType in ShapeType.entries) {
            val template = shapeType.template
            assertEquals(ShapeTemplate.POINT_COUNT, template.points.size, "shapeType=$shapeType")
        }
    }

    @Test
    fun `every registered ShapeType has a positive maxRadiusMeters`() {
        for (shapeType in ShapeType.entries) {
            assertTrue(shapeType.template.maxRadiusMeters > 0.0, "shapeType=$shapeType")
        }
    }

    @Test
    fun `resampled template is closed - first and last points coincide`() {
        for (shapeType in ShapeType.entries) {
            val points = shapeType.template.points
            val first = points.first()
            val last = points.last()
            assertEquals(first.lat, last.lat, 1e-9, "shapeType=$shapeType lat")
            assertEquals(first.lng, last.lng, 1e-9, "shapeType=$shapeType lng")
        }
    }

    @Test
    fun `fromRequestValue maps registered names case-sensitively and rejects unknowns`() {
        assertEquals(ShapeType.HEART, ShapeType.fromRequestValue("HEART"))
        assertEquals(ShapeType.CIRCLE, ShapeType.fromRequestValue("CIRCLE"))
        assertEquals(ShapeType.DROP, ShapeType.fromRequestValue("DROP"))

        assertNull(ShapeType.fromRequestValue("heart"))
        assertNull(ShapeType.fromRequestValue("TRIANGLE"))
        assertNull(ShapeType.fromRequestValue(null))
        assertNull(ShapeType.fromRequestValue(""))
        // docs/02-api-spec.md 13.3 — STAR was dropped from v1 (concave-vertex spikes), so it's an
        // unregistered name like any other now, not a special case.
        assertNull(ShapeType.fromRequestValue("STAR"))
    }

    @Test
    fun `a single closed subpath parses without error`() {
        val template = ShapeTemplate.fromSvgPath("M0,0 L100,0 L100,100 L0,100 Z")
        assertEquals(ShapeTemplate.POINT_COUNT, template.points.size)
        assertNotNull(template)
    }

    @Test
    fun `a path with more than one subpath is rejected`() {
        val twoSubpaths = "M0,0 L100,0 L100,100 Z M200,200 L300,200 L300,300 Z"
        assertFailsWith<IllegalStateException> {
            ShapeTemplate.fromSvgPath(twoSubpaths)
        }
    }

    @Test
    fun `quadratic and cubic curve commands are flattened without error`() {
        val withCurves = "M0,0 Q50,-50 100,0 C120,20 120,80 100,100 L0,100 Z"
        val template = ShapeTemplate.fromSvgPath(withCurves)
        assertEquals(ShapeTemplate.POINT_COUNT, template.points.size)
    }
}
