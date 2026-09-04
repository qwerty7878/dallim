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
        val receivedWaypoints = mutableListOf<List<LatLng>>()

        override suspend fun match(trace: List<LatLng>): OsrmRouteResult? {
            matchCallCount++
            if (matchShouldFail) error("match() should not have been called")
            return matchResult
        }

        override suspend fun route(waypoints: List<LatLng>): OsrmRouteResult {
            val idx = routeCallCount
            routeCallCount++
            receivedWaypoints += waypoints
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
            runBlocking { service.convertDrawnPath(DrawConvertRequest(drawnPath(9))) }
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
            runBlocking { service.convertDrawnPath(DrawConvertRequest(drawnPath(10))) }
        }
        assertEquals(ErrorCodes.DRAW_MATCH_FAILED, ex.code)
        assertEquals(HttpStatusCode.BadRequest, ex.status)
    }

    @Test
    fun `convertDrawnPath - successful match returns the snapped geometry and rounded distanceKm`() {
        val fake = FakeOsrmClient(matchResult = fakeResult(distanceMeters = 3421.7))
        val service = DiscoveryService(fake)

        val response = runBlocking { service.convertDrawnPath(DrawConvertRequest(drawnPath(10))) }
        assertEquals(3.42, response.distanceKm, 1e-9)
        assertEquals(1, fake.matchCallCount)
    }

    // ---------------------------------------------------------------------
    // draw-convert closeLoop option (11.1)
    // ---------------------------------------------------------------------

    @Test
    fun `convertDrawnPath - closeLoop false never calls route() even with a big gap`() {
        // First/last points ~1.1km apart (0.01 deg lat), well over the 15m threshold.
        val gappy = OsrmRouteResult(
            distanceMeters = 3421.7,
            geometry = GeoJsonLineString(coordinates = listOf(listOf(126.90, 37.40), listOf(126.90, 37.41))),
        )
        val fake = FakeOsrmClient(matchResult = gappy)
        val service = DiscoveryService(fake)

        val response = runBlocking { service.convertDrawnPath(DrawConvertRequest(drawnPath(10), closeLoop = false)) }

        assertEquals(3.42, response.distanceKm, 1e-9)
        assertEquals(0, fake.routeCallCount)
    }

    @Test
    fun `convertDrawnPath - closeLoop true with first-last gap under 15m skips the closing route() call`() {
        // ~0.0001 deg =~ 11m apart, under the 15m threshold.
        val almostClosed = OsrmRouteResult(
            distanceMeters = 3421.7,
            geometry = GeoJsonLineString(coordinates = listOf(listOf(126.90, 37.40), listOf(126.9001, 37.4001))),
        )
        val fake = FakeOsrmClient(matchResult = almostClosed)
        val service = DiscoveryService(fake)

        val response = runBlocking { service.convertDrawnPath(DrawConvertRequest(drawnPath(10), closeLoop = true)) }

        assertEquals(3.42, response.distanceKm, 1e-9)
        assertEquals(0, fake.routeCallCount)
    }

    @Test
    fun `convertDrawnPath - closeLoop true with a big gap appends a closing route() segment`() {
        // First/last points ~1.1km apart (0.01 deg lat), well over the 15m threshold.
        val gappy = OsrmRouteResult(
            distanceMeters = 3421.7,
            geometry = GeoJsonLineString(coordinates = listOf(listOf(126.90, 37.40), listOf(126.90, 37.41))),
        )
        val closingSegment = OsrmRouteResult(
            distanceMeters = 1100.0,
            geometry = GeoJsonLineString(coordinates = listOf(listOf(126.90, 37.41), listOf(126.90, 37.40))),
        )
        val fake = FakeOsrmClient(matchResult = gappy, routeResults = listOf(Result.success(closingSegment)))
        val service = DiscoveryService(fake)

        val response = runBlocking { service.convertDrawnPath(DrawConvertRequest(drawnPath(10), closeLoop = true)) }

        assertEquals(4.52, response.distanceKm, 1e-9) // 3421.7 + 1100.0 = 4521.7m
        assertEquals(1, fake.routeCallCount)
        assertEquals(4, response.geoJson.coordinates.size) // 2 matched points + 2 closing points appended
    }

    @Test
    fun `convertDrawnPath - closeLoop true with a failing closing route() call still returns the original match`() {
        val gappy = OsrmRouteResult(
            distanceMeters = 3421.7,
            geometry = GeoJsonLineString(coordinates = listOf(listOf(126.90, 37.40), listOf(126.90, 37.41))),
        )
        val fake = FakeOsrmClient(matchResult = gappy, routeResults = listOf(Result.failure(RuntimeException("no road"))))
        val service = DiscoveryService(fake)

        val response = runBlocking { service.convertDrawnPath(DrawConvertRequest(drawnPath(10), closeLoop = true)) }

        assertEquals(3.42, response.distanceKm, 1e-9)
        assertEquals(1, fake.routeCallCount)
        assertEquals(2, response.geoJson.coordinates.size)
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

    // ---------------------------------------------------------------------
    // discovery requiredWaypoint option (11.2)
    // ---------------------------------------------------------------------

    @Test
    fun `generateDiscoveryRoute - requiredWaypoint replaces exactly one of the K candidate waypoints`() {
        val fake = FakeOsrmClient(routeResults = listOf(Result.success(fakeResult(4900.0))))
        val service = DiscoveryService(fake)
        val required = LatLngDto(lat = 37.4200, lng = 126.9235) // due north of the start point

        runBlocking {
            service.generateDiscoveryRoute(
                DiscoveryRequest(startLng = 126.9235, startLat = 37.3905, targetDistanceKm = 5.0, requiredWaypoints = listOf(required)),
            )
        }

        // loop passed to route() is [start, wp1..wp5, start]; requiredWaypoint must appear exactly
        // once among the 5 middle waypoints, verbatim (not adjusted to any radius).
        val loop = fake.receivedWaypoints.single()
        val middle = loop.subList(1, loop.size - 1)
        assertEquals(5, middle.size)
        assertEquals(1, middle.count { it.lat == required.lat && it.lng == required.lng })
    }

    @Test
    fun `generateDiscoveryRoute - no requiredWaypoint means none of the candidates match an arbitrary point`() {
        val fake = FakeOsrmClient(routeResults = listOf(Result.success(fakeResult(4900.0))))
        val service = DiscoveryService(fake)

        runBlocking {
            service.generateDiscoveryRoute(DiscoveryRequest(startLng = 126.9235, startLat = 37.3905, targetDistanceKm = 5.0))
        }

        val loop = fake.receivedWaypoints.single()
        val middle = loop.subList(1, loop.size - 1)
        assertEquals(0, middle.count { it.lat == 37.4200 && it.lng == 126.9235 })
    }

    @Test
    fun `generateDiscoveryRoute - requiredWaypoint stays fixed across radius retries while free waypoints move`() {
        val fake = FakeOsrmClient(
            routeResults = listOf(
                Result.success(fakeResult(10_000.0)), // 2x target -> out of tolerance, triggers a radius-adjusted retry
                Result.success(fakeResult(4900.0)), // within tolerance -> stop
            ),
        )
        val service = DiscoveryService(fake)
        val required = LatLngDto(lat = 37.4200, lng = 126.9235)

        runBlocking {
            service.generateDiscoveryRoute(
                DiscoveryRequest(startLng = 126.9235, startLat = 37.3905, targetDistanceKm = 5.0, requiredWaypoints = listOf(required)),
            )
        }

        assertEquals(2, fake.routeCallCount)
        val firstMiddle = fake.receivedWaypoints[0].let { it.subList(1, it.size - 1) }
        val secondMiddle = fake.receivedWaypoints[1].let { it.subList(1, it.size - 1) }

        val fixedIndex = firstMiddle.indexOfFirst { it.lat == required.lat && it.lng == required.lng }
        assertTrue(fixedIndex >= 0, "requiredWaypoint should appear in the first attempt's waypoint list")
        assertEquals(firstMiddle[fixedIndex], secondMiddle[fixedIndex])

        val someFreeWaypointMoved = firstMiddle.indices.any { i -> i != fixedIndex && firstMiddle[i] != secondMiddle[i] }
        assertTrue(someFreeWaypointMoved, "free waypoints should move when the radius is adjusted between retries")
    }

    @Test
    fun `generateDiscoveryRoute - requiredWaypoint with no reachable route is still DISCOVERY_NO_ROUTE (422)`() {
        val fake = FakeOsrmClient(routeResults = List(5) { Result.failure(RuntimeException("no route")) })
        val service = DiscoveryService(fake)

        val ex = assertFailsWith<ApiException> {
            runBlocking {
                service.generateDiscoveryRoute(
                    DiscoveryRequest(
                        startLng = 0.0,
                        startLat = 0.0,
                        targetDistanceKm = 5.0,
                        requiredWaypoints = listOf(LatLngDto(lat = 0.01, lng = 0.01)),
                    ),
                )
            }
        }
        assertEquals(ErrorCodes.DISCOVERY_NO_ROUTE, ex.code)
        assertEquals(HttpStatusCode.UnprocessableEntity, ex.status)
    }

    @Test
    fun `generateDiscoveryRoute - multiple requiredWaypoints each get their own distinct slot`() {
        val fake = FakeOsrmClient(routeResults = listOf(Result.success(fakeResult(4900.0))))
        val service = DiscoveryService(fake)
        // due north and due east of the start point -> angularly far apart, should never contend
        // for the same candidate slot.
        val north = LatLngDto(lat = 37.4200, lng = 126.9235)
        val east = LatLngDto(lat = 37.3905, lng = 126.9500)

        runBlocking {
            service.generateDiscoveryRoute(
                DiscoveryRequest(
                    startLng = 126.9235,
                    startLat = 37.3905,
                    targetDistanceKm = 5.0,
                    requiredWaypoints = listOf(north, east),
                ),
            )
        }

        val middle = fake.receivedWaypoints.single().let { it.subList(1, it.size - 1) }
        assertEquals(5, middle.size)
        assertEquals(1, middle.count { it.lat == north.lat && it.lng == north.lng })
        assertEquals(1, middle.count { it.lat == east.lat && it.lng == east.lng })
    }

    @Test
    fun `generateDiscoveryRoute - more than 3 requiredWaypoints is a 400 VALIDATION_ERROR`() {
        val fake = FakeOsrmClient(routeResults = emptyList())
        val service = DiscoveryService(fake)
        val tooMany = (0 until 4).map { LatLngDto(lat = 37.39 + it * 0.001, lng = 126.92 + it * 0.001) }

        val ex = assertFailsWith<ApiException> {
            runBlocking {
                service.generateDiscoveryRoute(
                    DiscoveryRequest(
                        startLng = 126.9235,
                        startLat = 37.3905,
                        targetDistanceKm = 5.0,
                        requiredWaypoints = tooMany,
                    ),
                )
            }
        }
        assertEquals(ErrorCodes.VALIDATION_ERROR, ex.code)
        assertEquals(HttpStatusCode.BadRequest, ex.status)
        assertEquals(0, fake.routeCallCount, "should reject before ever calling OSRM")
    }

    // ---------------------------------------------------------------------
    // discovery point-to-point mode (11.4)
    // ---------------------------------------------------------------------

    private fun p2pRequest(targetDistanceKm: Double = 5.0) = DiscoveryRequest(
        startLng = 126.9235,
        startLat = 37.3905,
        targetDistanceKm = targetDistanceKm,
        mode = "POINT_TO_POINT",
        endLat = 37.4200,
        endLng = 126.9500,
    )

    @Test
    fun `generateDiscoveryRoute - point-to-point returns the direct route unmodified when it already meets the target`() {
        val fake = FakeOsrmClient(routeResults = listOf(Result.success(fakeResult(5200.0))))
        val service = DiscoveryService(fake)

        val response = runBlocking { service.generateDiscoveryRoute(p2pRequest()) }

        assertEquals(1, fake.routeCallCount, "a direct route already within tolerance should never trigger a detour call")
        assertEquals(5.2, response.distanceKm)
        // direct call is exactly [start, end], no detour point in the middle
        assertEquals(2, fake.receivedWaypoints.single().size)
    }

    @Test
    fun `generateDiscoveryRoute - point-to-point adds a detour when the direct route falls short`() {
        val fake = FakeOsrmClient(
            routeResults = listOf(
                Result.success(fakeResult(2000.0)), // direct: way under the 5km target
                Result.success(fakeResult(4900.0)), // detour attempt: within tolerance -> stop
            ),
        )
        val service = DiscoveryService(fake)

        val response = runBlocking { service.generateDiscoveryRoute(p2pRequest()) }

        assertEquals(2, fake.routeCallCount)
        assertEquals(2, fake.receivedWaypoints[0].size, "first call is the direct [start, end] probe")
        assertEquals(3, fake.receivedWaypoints[1].size, "detour call is [start, detour, end]")
        assertEquals(4.9, response.distanceKm)
    }

    @Test
    fun `generateDiscoveryRoute - point-to-point without endLat-endLng is a 400 VALIDATION_ERROR`() {
        val fake = FakeOsrmClient(routeResults = emptyList())
        val service = DiscoveryService(fake)

        val ex = assertFailsWith<ApiException> {
            runBlocking {
                service.generateDiscoveryRoute(
                    DiscoveryRequest(startLng = 126.9235, startLat = 37.3905, targetDistanceKm = 5.0, mode = "POINT_TO_POINT"),
                )
            }
        }
        assertEquals(ErrorCodes.VALIDATION_ERROR, ex.code)
        assertEquals(HttpStatusCode.BadRequest, ex.status)
        assertEquals(0, fake.routeCallCount)
    }

    @Test
    fun `generateDiscoveryRoute - unknown mode is a 400 VALIDATION_ERROR`() {
        val fake = FakeOsrmClient(routeResults = emptyList())
        val service = DiscoveryService(fake)

        val ex = assertFailsWith<ApiException> {
            runBlocking {
                service.generateDiscoveryRoute(
                    DiscoveryRequest(startLng = 126.9235, startLat = 37.3905, targetDistanceKm = 5.0, mode = "BOGUS"),
                )
            }
        }
        assertEquals(ErrorCodes.VALIDATION_ERROR, ex.code)
        assertEquals(HttpStatusCode.BadRequest, ex.status)
    }

    @Test
    fun `generateDiscoveryRoute - point-to-point with no route between start and end is DISCOVERY_NO_ROUTE (422)`() {
        val fake = FakeOsrmClient(routeResults = listOf(Result.failure(RuntimeException("no route"))))
        val service = DiscoveryService(fake)

        val ex = assertFailsWith<ApiException> {
            runBlocking { service.generateDiscoveryRoute(p2pRequest()) }
        }
        assertEquals(ErrorCodes.DISCOVERY_NO_ROUTE, ex.code)
        assertEquals(HttpStatusCode.UnprocessableEntity, ex.status)
    }

    // ---------------------------------------------------------------------
    // discovery point-to-point + requiredWaypoints combination (11.6)
    // ---------------------------------------------------------------------

    @Test
    fun `generateDiscoveryRoute - point-to-point with waypoints sorts by (distance-to-start minus distance-to-end) ascending`() {
        val fake = FakeOsrmClient(routeResults = listOf(Result.success(fakeResult(5200.0))))
        val service = DiscoveryService(fake)

        // near start (close to start, far from end) -> should sort first.
        val nearStart = LatLngDto(lat = 37.3910, lng = 126.9240)
        // near end (far from start, close to end) -> should sort last.
        val nearEnd = LatLngDto(lat = 37.4190, lng = 126.9490)

        // Deliberately submitted in end->start order to prove the service re-sorts them.
        runBlocking {
            service.generateDiscoveryRoute(p2pRequest().copy(requiredWaypoints = listOf(nearEnd, nearStart)))
        }

        val route = fake.receivedWaypoints.single()
        // [start, nearStart, nearEnd, end]
        assertEquals(4, route.size)
        assertEquals(nearStart.lat, route[1].lat, 1e-9)
        assertEquals(nearStart.lng, route[1].lng, 1e-9)
        assertEquals(nearEnd.lat, route[2].lat, 1e-9)
        assertEquals(nearEnd.lng, route[2].lng, 1e-9)
    }

    @Test
    fun `generateDiscoveryRoute - point-to-point with waypoints returns the combined route unmodified when it already meets the target`() {
        val fake = FakeOsrmClient(routeResults = listOf(Result.success(fakeResult(5200.0))))
        val service = DiscoveryService(fake)
        val waypoint = LatLngDto(lat = 37.4000, lng = 126.9300)

        val response = runBlocking {
            service.generateDiscoveryRoute(p2pRequest().copy(requiredWaypoints = listOf(waypoint)))
        }

        assertEquals(1, fake.routeCallCount, "a combined route already within tolerance should never trigger a detour call")
        assertEquals(5.2, response.distanceKm)
        assertEquals(3, fake.receivedWaypoints.single().size) // [start, waypoint, end], no detour
    }

    @Test
    fun `generateDiscoveryRoute - point-to-point with waypoints adds exactly one detour when the combined route falls short`() {
        val fake = FakeOsrmClient(
            routeResults = listOf(
                Result.success(fakeResult(2000.0)), // combined start->waypoint->end: way under the 5km target
                Result.success(fakeResult(4900.0)), // detour attempt: within tolerance -> stop
            ),
        )
        val service = DiscoveryService(fake)
        val waypoint = LatLngDto(lat = 37.4000, lng = 126.9300)

        val response = runBlocking {
            service.generateDiscoveryRoute(p2pRequest().copy(requiredWaypoints = listOf(waypoint)))
        }

        assertEquals(2, fake.routeCallCount)
        assertEquals(3, fake.receivedWaypoints[0].size, "first call is the un-detoured [start, waypoint, end] probe")
        assertEquals(4, fake.receivedWaypoints[1].size, "detour call adds exactly one extra point")
        assertEquals(4.9, response.distanceKm)
    }

    @Test
    fun `generateDiscoveryRoute - point-to-point with waypoints falling short retries up to 5 times then returns the last result`() {
        val fake = FakeOsrmClient(
            routeResults = listOf(Result.success(fakeResult(2000.0))) + List(5) { Result.success(fakeResult(2500.0)) },
        )
        val service = DiscoveryService(fake)
        val waypoint = LatLngDto(lat = 37.4000, lng = 126.9300)

        runBlocking {
            service.generateDiscoveryRoute(p2pRequest().copy(requiredWaypoints = listOf(waypoint)))
        }

        // 1 initial probe + up to MAX_ATTEMPTS(5) detour retries, all persistently out of tolerance.
        assertEquals(6, fake.routeCallCount)
    }

    @Test
    fun `generateDiscoveryRoute - point-to-point with more than 3 requiredWaypoints is still a 400 VALIDATION_ERROR`() {
        val fake = FakeOsrmClient(routeResults = emptyList())
        val service = DiscoveryService(fake)
        val tooMany = (0 until 4).map { LatLngDto(lat = 37.39 + it * 0.001, lng = 126.92 + it * 0.001) }

        val ex = assertFailsWith<ApiException> {
            runBlocking {
                service.generateDiscoveryRoute(p2pRequest().copy(requiredWaypoints = tooMany))
            }
        }
        assertEquals(ErrorCodes.VALIDATION_ERROR, ex.code)
        assertEquals(HttpStatusCode.BadRequest, ex.status)
        assertEquals(0, fake.routeCallCount, "should reject before ever calling OSRM")
    }

    @Test
    fun `generateDiscoveryRoute - point-to-point with waypoints and no reachable route is DISCOVERY_NO_ROUTE (422)`() {
        val fake = FakeOsrmClient(routeResults = listOf(Result.failure(RuntimeException("no route"))))
        val service = DiscoveryService(fake)
        val waypoint = LatLngDto(lat = 37.4000, lng = 126.9300)

        val ex = assertFailsWith<ApiException> {
            runBlocking {
                service.generateDiscoveryRoute(p2pRequest().copy(requiredWaypoints = listOf(waypoint)))
            }
        }
        assertEquals(ErrorCodes.DISCOVERY_NO_ROUTE, ex.code)
        assertEquals(HttpStatusCode.UnprocessableEntity, ex.status)
        assertEquals(1, fake.routeCallCount, "the combined-route probe failing should not trigger any detour retries")
    }

    // ---------------------------------------------------------------------
    // discovery SHAPE mode (13.1/13.2/13.3)
    // ---------------------------------------------------------------------

    private fun shapeRequest(
        shapeType: String? = "HEART",
        priority: String = "DISTANCE",
        targetDistanceKm: Double = 5.0,
    ) = DiscoveryRequest(
        startLng = 126.9235,
        startLat = 37.3905,
        targetDistanceKm = targetDistanceKm,
        mode = "SHAPE",
        shapeType = shapeType,
        priority = priority,
    )

    @Test
    fun `generateDiscoveryRoute - SHAPE without shapeType is a 400 VALIDATION_ERROR`() {
        val normal = FakeOsrmClient(routeResults = emptyList())
        val shape = FakeOsrmClient(routeResults = emptyList())
        val service = DiscoveryService(normal, shape)

        val ex = assertFailsWith<ApiException> {
            runBlocking { service.generateDiscoveryRoute(shapeRequest(shapeType = null)) }
        }
        assertEquals(ErrorCodes.VALIDATION_ERROR, ex.code)
        assertEquals(HttpStatusCode.BadRequest, ex.status)
        assertEquals(0, shape.routeCallCount)
    }

    @Test
    fun `generateDiscoveryRoute - SHAPE with an unregistered shapeType is a 400 VALIDATION_ERROR`() {
        val normal = FakeOsrmClient(routeResults = emptyList())
        val shape = FakeOsrmClient(routeResults = emptyList())
        val service = DiscoveryService(normal, shape)

        val ex = assertFailsWith<ApiException> {
            runBlocking { service.generateDiscoveryRoute(shapeRequest(shapeType = "TRIANGLE")) }
        }
        assertEquals(ErrorCodes.VALIDATION_ERROR, ex.code)
        assertEquals(HttpStatusCode.BadRequest, ex.status)
        assertEquals(0, shape.routeCallCount)
    }

    @Test
    fun `generateDiscoveryRoute - SHAPE with an invalid priority is a 400 VALIDATION_ERROR`() {
        val normal = FakeOsrmClient(routeResults = emptyList())
        val shape = FakeOsrmClient(routeResults = emptyList())
        val service = DiscoveryService(normal, shape)

        val ex = assertFailsWith<ApiException> {
            runBlocking { service.generateDiscoveryRoute(shapeRequest(priority = "BOGUS")) }
        }
        assertEquals(ErrorCodes.VALIDATION_ERROR, ex.code)
        assertEquals(HttpStatusCode.BadRequest, ex.status)
        assertEquals(0, shape.routeCallCount)
    }

    @Test
    fun `generateDiscoveryRoute - SHAPE combined with requiredWaypoints is a 400 VALIDATION_ERROR (13_4)`() {
        val normal = FakeOsrmClient(routeResults = emptyList())
        val shape = FakeOsrmClient(routeResults = emptyList())
        val service = DiscoveryService(normal, shape)
        val required = LatLngDto(lat = 37.4000, lng = 126.9300)

        val ex = assertFailsWith<ApiException> {
            runBlocking {
                service.generateDiscoveryRoute(shapeRequest().copy(requiredWaypoints = listOf(required)))
            }
        }
        assertEquals(ErrorCodes.VALIDATION_ERROR, ex.code)
        assertEquals(HttpStatusCode.BadRequest, ex.status)
        assertEquals(0, shape.routeCallCount)
    }

    @Test
    fun `generateDiscoveryRoute - SHAPE combined with endLat-endLng is a 400 VALIDATION_ERROR (13_4)`() {
        val normal = FakeOsrmClient(routeResults = emptyList())
        val shape = FakeOsrmClient(routeResults = emptyList())
        val service = DiscoveryService(normal, shape)

        val ex = assertFailsWith<ApiException> {
            runBlocking {
                service.generateDiscoveryRoute(shapeRequest().copy(endLat = 37.42, endLng = 126.95))
            }
        }
        assertEquals(ErrorCodes.VALIDATION_ERROR, ex.code)
        assertEquals(HttpStatusCode.BadRequest, ex.status)
        assertEquals(0, shape.routeCallCount)
    }

    @Test
    fun `generateDiscoveryRoute - SHAPE routes through the dedicated shape OSRM client only`() {
        val normal = FakeOsrmClient(routeResults = emptyList())
        val shape = FakeOsrmClient(routeResults = listOf(Result.success(fakeResult(4900.0))))
        val service = DiscoveryService(normal, shape)

        val response = runBlocking { service.generateDiscoveryRoute(shapeRequest()) }

        assertEquals(0, normal.routeCallCount, "SHAPE mode must never call the default OSRM instance")
        assertEquals(1, shape.routeCallCount)
        assertEquals(4.9, response.distanceKm, 1e-9)
    }

    @Test
    fun `generateDiscoveryRoute - SHAPE waypoints start and end at the request's start point`() {
        val normal = FakeOsrmClient(routeResults = emptyList())
        val shape = FakeOsrmClient(routeResults = listOf(Result.success(fakeResult(4900.0))))
        val service = DiscoveryService(normal, shape)

        runBlocking { service.generateDiscoveryRoute(shapeRequest()) }

        val waypoints = shape.receivedWaypoints.single()
        assertEquals(ShapeTemplate.POINT_COUNT, waypoints.size)
        val start = LatLng(lat = 37.3905, lng = 126.9235)
        assertEquals(start, waypoints.first())
        // The template's own first/last points coincide (ShapeTemplatesTest), so the whole mapped
        // loop should start and end at `start` too.
        assertEquals(start.lat, waypoints.last().lat, 1e-6)
        assertEquals(start.lng, waypoints.last().lng, 1e-6)
    }

    @Test
    fun `generateDiscoveryRoute - SHAPE priority DISTANCE (default) retries up to 5 times when persistently out of tolerance`() {
        val normal = FakeOsrmClient(routeResults = emptyList())
        val shape = FakeOsrmClient(routeResults = List(5) { Result.success(fakeResult(50_000.0)) }) // way over target every time
        val service = DiscoveryService(normal, shape)

        runBlocking { service.generateDiscoveryRoute(shapeRequest(priority = "DISTANCE")) }

        assertEquals(5, shape.routeCallCount)
    }

    @Test
    fun `generateDiscoveryRoute - SHAPE priority SHAPE caps retries at 2 when persistently out of tolerance`() {
        val normal = FakeOsrmClient(routeResults = emptyList())
        val shape = FakeOsrmClient(routeResults = List(2) { Result.success(fakeResult(50_000.0)) })
        val service = DiscoveryService(normal, shape)

        val response = runBlocking { service.generateDiscoveryRoute(shapeRequest(priority = "SHAPE")) }

        assertEquals(2, shape.routeCallCount)
        assertEquals(50.0, response.distanceKm, 1e-9)
    }

    @Test
    fun `generateDiscoveryRoute - SHAPE first attempt within tolerance stops the retry loop immediately`() {
        val normal = FakeOsrmClient(routeResults = emptyList())
        val shape = FakeOsrmClient(routeResults = listOf(Result.success(fakeResult(4900.0))))
        val service = DiscoveryService(normal, shape)

        runBlocking { service.generateDiscoveryRoute(shapeRequest(priority = "SHAPE")) }

        assertEquals(1, shape.routeCallCount)
    }

    @Test
    fun `generateDiscoveryRoute - SHAPE with every route() attempt failing is DISCOVERY_NO_ROUTE (422)`() {
        val normal = FakeOsrmClient(routeResults = emptyList())
        val shape = FakeOsrmClient(routeResults = List(5) { Result.failure(RuntimeException("no route")) })
        val service = DiscoveryService(normal, shape)

        val ex = assertFailsWith<ApiException> {
            runBlocking { service.generateDiscoveryRoute(shapeRequest(priority = "DISTANCE")) }
        }
        assertEquals(ErrorCodes.DISCOVERY_NO_ROUTE, ex.code)
        assertEquals(HttpStatusCode.UnprocessableEntity, ex.status)
        assertEquals(5, shape.routeCallCount)
    }

    @Test
    fun `generateDiscoveryRoute - SHAPE accepts CIRCLE, DROP and STAR shapeTypes too`() {
        for (shapeType in listOf("CIRCLE", "DROP", "STAR")) {
            val normal = FakeOsrmClient(routeResults = emptyList())
            val shape = FakeOsrmClient(routeResults = listOf(Result.success(fakeResult(4900.0))))
            val service = DiscoveryService(normal, shape)

            val response = runBlocking { service.generateDiscoveryRoute(shapeRequest(shapeType = shapeType)) }
            assertEquals(4.9, response.distanceKm, 1e-9, "shapeType=$shapeType")
        }
    }
}
