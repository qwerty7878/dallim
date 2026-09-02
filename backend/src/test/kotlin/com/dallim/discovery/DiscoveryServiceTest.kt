package com.dallim.discovery

import com.dallim.common.ApiException
import com.dallim.common.ErrorCodes
import com.dallim.common.GeoJsonLineString
import com.dallim.common.HttpClientFactory
import com.dallim.common.LatLng
import com.dallim.common.OsrmClient
import com.dallim.common.OsrmRouteResult
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for [DiscoveryService]'s request-validation and retry/error branching
 * (docs/02-api-spec.md 8장) against a fake [OsrmClient] — no real OSRM/network call. These pin
 * down the 400/422 decisions and the discovery retry loop exactly, independent of whatever a
 * real OSRM instance happens to return for a given coordinate (verified separately via curl
 * against the actually-running localhost:5001 OSRM per the harness's manual verification step).
 */
class DiscoveryServiceTest {

    private fun drawnPath(count: Int): GeoJsonLineString =
        GeoJsonLineString(coordinates = (0 until count).map { listOf(126.9235 + it * 0.0001, 37.3905 + it * 0.0001) })

    /** A programmable fake — never touches the network (methods are fully overridden, no super calls). */
    private class FakeOsrmClient(
        private val matchResult: OsrmRouteResult? = null,
        private val matchShouldFail: Boolean = false,
        private val routeResults: List<Result<OsrmRouteResult>> = emptyList(),
    ) : OsrmClient(HttpClientFactory.create(), "http://unused") {
        var matchCallCount = 0
        var routeCallCount = 0

        override suspend fun match(trace: List<LatLng>): OsrmRouteResult? {
            matchCallCount++
            if (matchShouldFail) error("match() should not have been called")
            return matchResult
        }

        override suspend fun route(waypoints: List<LatLng>): OsrmRouteResult {
            val idx = routeCallCount
            routeCallCount++
            if (idx >= routeResults.size) error("route() called more times than the fake was programmed for (call #${idx + 1})")
            return routeResults[idx].getOrThrow()
        }
    }

    private fun fakeResult(distanceMeters: Double) =
        OsrmRouteResult(distanceMeters = distanceMeters, geometry = GeoJsonLineString(coordinates = listOf(listOf(126.9, 37.4), listOf(126.91, 37.41))))

    // ---------------------------------------------------------------------
    // draw-convert (8.1)
    // ---------------------------------------------------------------------

    @Test
    fun `convertDrawnPath - fewer than 10 points is DRAW_TOO_SHORT without calling OSRM`() {
        val fake = FakeOsrmClient(matchShouldFail = true)
        val service = DiscoveryService(fake)

        val ex = assertFailsWith<ApiException> {
            runBlocking { service.convertDrawnPath(drawnPath(9)) }
        }
        assertEquals(ErrorCodes.DRAW_TOO_SHORT, ex.code)
        assertEquals(HttpStatusCode.BadRequest, ex.status)
        assertEquals(0, fake.matchCallCount)
    }

    @Test
    fun `convertDrawnPath - OSRM NoMatch (null) is DRAW_MATCH_FAILED`() {
        val fake = FakeOsrmClient(matchResult = null)
        val service = DiscoveryService(fake)

        val ex = assertFailsWith<ApiException> {
            runBlocking { service.convertDrawnPath(drawnPath(10)) }
        }
        assertEquals(ErrorCodes.DRAW_MATCH_FAILED, ex.code)
        assertEquals(HttpStatusCode.BadRequest, ex.status)
    }

    @Test
    fun `convertDrawnPath - successful match returns the snapped geometry and rounded distanceKm`() {
        val fake = FakeOsrmClient(matchResult = fakeResult(distanceMeters = 3421.7))
        val service = DiscoveryService(fake)

        val response = runBlocking { service.convertDrawnPath(drawnPath(10)) }
        assertEquals(3.42, response.distanceKm, 1e-9)
        assertEquals(1, fake.matchCallCount)
    }

    // ---------------------------------------------------------------------
    // discovery (8.2)
    // ---------------------------------------------------------------------

    @Test
    fun `generateDiscoveryRoute - first attempt within tolerance stops the retry loop immediately`() {
        // target 5km; 4.9km is within +-15%.
        val fake = FakeOsrmClient(routeResults = listOf(Result.success(fakeResult(4900.0))))
        val service = DiscoveryService(fake)

        val response = runBlocking {
            service.generateDiscoveryRoute(DiscoveryRequest(startLng = 126.9235, startLat = 37.3905, targetDistanceKm = 5.0))
        }

        assertEquals(4.9, response.distanceKm, 1e-9)
        assertEquals(1, fake.routeCallCount)
        assertEquals(Math.round(4.9 * 7.0).toInt(), response.estimatedMinutes)
    }

    @Test
    fun `generateDiscoveryRoute - out-of-tolerance results retry up to 5 times then return the last result`() {
        // target 5km; every attempt returns 10km (2x target, well outside +-15%) so it always retries.
        val fake = FakeOsrmClient(routeResults = List(5) { Result.success(fakeResult(10_000.0)) })
        val service = DiscoveryService(fake)

        val response = runBlocking {
            service.generateDiscoveryRoute(DiscoveryRequest(startLng = 126.9235, startLat = 37.3905, targetDistanceKm = 5.0))
        }

        assertEquals(5, fake.routeCallCount)
        assertEquals(10.0, response.distanceKm, 1e-9)
    }

    @Test
    fun `generateDiscoveryRoute - every route() attempt failing is DISCOVERY_NO_ROUTE (422)`() {
        val fake = FakeOsrmClient(routeResults = List(5) { Result.failure(RuntimeException("no route")) })
        val service = DiscoveryService(fake)

        val ex = assertFailsWith<ApiException> {
            runBlocking {
                service.generateDiscoveryRoute(DiscoveryRequest(startLng = 0.0, startLat = 0.0, targetDistanceKm = 5.0))
            }
        }
        assertEquals(ErrorCodes.DISCOVERY_NO_ROUTE, ex.code)
        assertEquals(HttpStatusCode.UnprocessableEntity, ex.status)
        assertEquals(5, fake.routeCallCount)
    }

    @Test
    fun `generateDiscoveryRoute - a mid-loop failure is tolerated as long as one attempt eventually succeeds`() {
        val fake = FakeOsrmClient(
            routeResults = listOf(
                Result.failure(RuntimeException("transient")),
                Result.success(fakeResult(5100.0)), // within tolerance of 5km target
            ),
        )
        val service = DiscoveryService(fake)

        val response = runBlocking {
            service.generateDiscoveryRoute(DiscoveryRequest(startLng = 126.9235, startLat = 37.3905, targetDistanceKm = 5.0))
        }

        assertEquals(5.1, response.distanceKm, 1e-9)
        assertEquals(2, fake.routeCallCount)
    }

    @Test
    fun `generateDiscoveryRoute - pace field is accepted but does not affect the result`() {
        val fake = FakeOsrmClient(routeResults = listOf(Result.success(fakeResult(5000.0))))
        val service = DiscoveryService(fake)

        val response = runBlocking {
            service.generateDiscoveryRoute(
                DiscoveryRequest(startLng = 126.9235, startLat = 37.3905, targetDistanceKm = 5.0, pace = "PACE_6_7"),
            )
        }
        assertTrue(response.distanceKm > 0)
    }
}
