package com.dallim.discovery

import com.dallim.common.GeoJsonLineString
import com.dallim.common.LatLng
import com.dallim.common.OsrmRouteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Unit tests for [selectBestShapeCandidate] (docs/02-api-spec.md 13.1.2) — the pure "candidate
 * list -> best candidate" decision extracted out of [DiscoveryService.generateShapeRoute]
 * specifically so it can be exercised with hand-built fake data, no [com.dallim.common.OsrmClient]
 * or network involved at all.
 */
class SelectBestShapeCandidateTest {

    /** A small closed square polyline, used as a stand-in "shape" — offsettable so two squares can be made to differ. */
    private fun square(offsetLat: Double = 0.0, offsetLng: Double = 0.0, sizeDeg: Double = 0.001): List<LatLng> =
        listOf(
            LatLng(lat = 37.0 + offsetLat, lng = 127.0 + offsetLng),
            LatLng(lat = 37.0 + offsetLat + sizeDeg, lng = 127.0 + offsetLng),
            LatLng(lat = 37.0 + offsetLat + sizeDeg, lng = 127.0 + offsetLng + sizeDeg),
            LatLng(lat = 37.0 + offsetLat, lng = 127.0 + offsetLng + sizeDeg),
            LatLng(lat = 37.0 + offsetLat, lng = 127.0 + offsetLng),
        )

    private fun candidate(templatePoints: List<LatLng>, routeGeometryPoints: List<LatLng>, distanceMeters: Double) =
        ShapeRouteCandidate(
            templatePoints = templatePoints,
            routeResult = OsrmRouteResult(
                distanceMeters = distanceMeters,
                geometry = GeoJsonLineString(coordinates = routeGeometryPoints.map { listOf(it.lng, it.lat) }),
            ),
        )

    @Test
    fun `picks the candidate whose routed geometry matches its own template most closely`() {
        val template = square()

        // Candidate A: routed geometry is identical to its own template -> Frechet distance ~0.
        val closeMatch = candidate(templatePoints = template, routeGeometryPoints = template, distanceMeters = 1000.0)

        // Candidate B: same-shaped square, but shifted ~1.1km away (0.01 deg) -> large Frechet distance.
        val farMismatch = candidate(
            templatePoints = template,
            routeGeometryPoints = square(offsetLat = 0.01, offsetLng = 0.01),
            distanceMeters = 2000.0,
        )

        // Order shouldn't matter -> try both orderings.
        assertEquals(1000.0, selectBestShapeCandidate(listOf(farMismatch, closeMatch)).routeResult.distanceMeters, 1e-9)
        assertEquals(1000.0, selectBestShapeCandidate(listOf(closeMatch, farMismatch)).routeResult.distanceMeters, 1e-9)
    }

    @Test
    fun `picks the smallest of more than two candidates, not just the smallest of the first pair`() {
        val template = square()
        val worst = candidate(templatePoints = template, routeGeometryPoints = square(offsetLat = 0.05), distanceMeters = 1.0)
        val mediocre = candidate(templatePoints = template, routeGeometryPoints = square(offsetLat = 0.02), distanceMeters = 2.0)
        val best = candidate(templatePoints = template, routeGeometryPoints = square(offsetLat = 0.0001), distanceMeters = 3.0)

        val winner = selectBestShapeCandidate(listOf(worst, mediocre, best))

        assertEquals(3.0, winner.routeResult.distanceMeters, 1e-9)
    }

    @Test
    fun `a single candidate is returned unconditionally regardless of how mangled its geometry is`() {
        val template = square()
        val only = candidate(templatePoints = template, routeGeometryPoints = square(offsetLat = 5.0), distanceMeters = 42_000.0)

        val winner = selectBestShapeCandidate(listOf(only))

        assertEquals(42_000.0, winner.routeResult.distanceMeters, 1e-9)
    }

    @Test
    fun `throws on an empty candidate list rather than silently returning null`() {
        assertFailsWith<IllegalArgumentException> {
            selectBestShapeCandidate(emptyList())
        }
    }
}
