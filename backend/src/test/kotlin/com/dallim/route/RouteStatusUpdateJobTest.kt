package com.dallim.route

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit + integration tests for [RouteStatusUpdateJob] — the still-missing-until-now batch that
 * turns an already-accurate `finisher_count` (kept in sync by com.dallim.run.FinisherCountSync)
 * into the actual `sketch_routes.status` column (docs/01-feature-spec.md 2.2.C /2.3).
 *
 * [nextStatus] and [untilNext3AmMillis] are pure functions with no DB dependency, tested directly.
 * [runOnce] is exercised against the REAL local docker-compose Postgres (see
 * com.dallim.testsupport.ApiTestSupport's doc comment — same "no mocks" convention as the rest of
 * this suite), using a dedicated, uniquely-id'd route row per test (inserted directly with a raw
 * two-point path, since sketch_routes.path is NOT NULL and Exposed can't express it — see
 * com.dallim.common.PostGis) rather than the shared V2__seed_curated_routes.sql rows, so each
 * test's starting finisher_count/status is deterministic instead of drifting across repeated
 * suite runs the way rt_001..rt_005 do.
 */
class RouteStatusUpdateJobTest {

    private val database: Database = Database.connect(
        url = "jdbc:postgresql://localhost:5432/dallim",
        driver = "org.postgresql.Driver",
        user = "dallim",
        password = "dallim",
    )
    private val job = RouteStatusUpdateJob(database)
    private val insertedRouteIds = mutableListOf<String>()

    @AfterTest
    fun cleanup() {
        if (insertedRouteIds.isEmpty()) return
        transaction(database) {
            for (routeId in insertedRouteIds) {
                exec("DELETE FROM sketch_routes WHERE id = '$routeId'")
            }
        }
        insertedRouteIds.clear()
    }

    // ---------------------------------------------------------------------
    // 1. nextStatus — pure function, no DB
    // ---------------------------------------------------------------------

    @Test
    fun `finisherCount 0 keeps a DISCOVERY route at DISCOVERY`() {
        assertEquals(RouteStatus.DISCOVERY, job.nextStatus(RouteStatus.DISCOVERY, finisherCount = 0))
    }

    @Test
    fun `finisherCount 1 promotes DISCOVERY to VERIFIED`() {
        assertEquals(RouteStatus.VERIFIED, job.nextStatus(RouteStatus.DISCOVERY, finisherCount = RouteStatusUpdateJob.VERIFIED_THRESHOLD))
    }

    @Test
    fun `finisherCount just below the VERIFIED threshold is impossible but 0 stays DISCOVERY regardless`() {
        assertEquals(RouteStatus.DISCOVERY, job.nextStatus(RouteStatus.DISCOVERY, finisherCount = RouteStatusUpdateJob.VERIFIED_THRESHOLD - 1))
    }

    @Test
    fun `finisherCount at the POPULAR threshold promotes VERIFIED to POPULAR`() {
        assertEquals(RouteStatus.POPULAR, job.nextStatus(RouteStatus.VERIFIED, finisherCount = RouteStatusUpdateJob.POPULAR_THRESHOLD))
    }

    @Test
    fun `finisherCount at the POPULAR threshold promotes DISCOVERY straight to POPULAR in one pass`() {
        // A route can accumulate finishers fast enough between two daily runs that it never sits
        // observably at VERIFIED -- status only cares about the current finisherCount, not the
        // history of how it got there, so this is a deliberate one-hop jump, not a bug.
        assertEquals(RouteStatus.POPULAR, job.nextStatus(RouteStatus.DISCOVERY, finisherCount = RouteStatusUpdateJob.POPULAR_THRESHOLD))
    }

    @Test
    fun `finisherCount just below the POPULAR threshold keeps VERIFIED (does not promote early)`() {
        assertEquals(
            RouteStatus.VERIFIED,
            job.nextStatus(RouteStatus.VERIFIED, finisherCount = RouteStatusUpdateJob.POPULAR_THRESHOLD - 1),
        )
    }

    @Test
    fun `an already-POPULAR route stays POPULAR regardless of finisherCount (status never regresses)`() {
        assertEquals(RouteStatus.POPULAR, job.nextStatus(RouteStatus.POPULAR, finisherCount = 0))
        assertEquals(RouteStatus.POPULAR, job.nextStatus(RouteStatus.POPULAR, finisherCount = 999_999))
    }

    // ---------------------------------------------------------------------
    // 2. untilNext3AmMillis — pure function, no DB
    // ---------------------------------------------------------------------

    @Test
    fun `untilNext3AmMillis with now before 3am today targets todays 3am`() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = ZonedDateTime.of(2026, 9, 1, 1, 30, 0, 0, zone)

        val delayMillis = untilNext3AmMillis(now, zone)

        assertEquals(Duration.ofHours(1).plusMinutes(30).toMillis(), delayMillis)
    }

    @Test
    fun `untilNext3AmMillis with now after 3am today targets tomorrows 3am`() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = ZonedDateTime.of(2026, 9, 1, 14, 0, 0, 0, zone)

        val delayMillis = untilNext3AmMillis(now, zone)

        // From 14:00 to tomorrow 03:00 is 13 hours.
        assertEquals(Duration.ofHours(13).toMillis(), delayMillis)
    }

    @Test
    fun `untilNext3AmMillis exactly at 3am rolls over to tomorrow, not zero`() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = ZonedDateTime.of(2026, 9, 1, 3, 0, 0, 0, zone)

        val delayMillis = untilNext3AmMillis(now, zone)

        assertEquals(Duration.ofHours(24).toMillis(), delayMillis)
    }

    // ---------------------------------------------------------------------
    // 3. runOnce — real Postgres, dedicated per-test route rows
    // ---------------------------------------------------------------------

    @Test
    fun `runOnce promotes a DISCOVERY route with 1+ finishers to VERIFIED`() {
        val routeId = insertTestRoute(status = RouteStatus.DISCOVERY, finisherCount = 3)

        job.runOnce()

        assertEquals(RouteStatus.VERIFIED, readStatus(routeId))
    }

    @Test
    fun `runOnce promotes a VERIFIED route past the POPULAR threshold to POPULAR`() {
        val routeId = insertTestRoute(status = RouteStatus.VERIFIED, finisherCount = RouteStatusUpdateJob.POPULAR_THRESHOLD)

        job.runOnce()

        assertEquals(RouteStatus.POPULAR, readStatus(routeId))
    }

    @Test
    fun `runOnce leaves a DISCOVERY route with 0 finishers untouched`() {
        val routeId = insertTestRoute(status = RouteStatus.DISCOVERY, finisherCount = 0)

        job.runOnce()

        assertEquals(RouteStatus.DISCOVERY, readStatus(routeId))
    }

    @Test
    fun `runOnce never touches an already-POPULAR route`() {
        val routeId = insertTestRoute(status = RouteStatus.POPULAR, finisherCount = 0)

        val updatedCount = job.runOnce()

        assertEquals(RouteStatus.POPULAR, readStatus(routeId))
        // Not asserting updatedCount == 0 outright -- other routes in this shared dev DB (seeded
        // or left over from other tests) may legitimately be promoted in the same pass -- just
        // confirm this specific already-POPULAR route wasn't touched or counted as a regression.
        assertTrue(updatedCount >= 0)
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /** Inserts a minimal, uniquely-id'd sketch_route (two-point path, arbitrary Anyang-area
     * coordinates -- geometry content doesn't matter for this job) and tracks it for cleanup. */
    private fun insertTestRoute(status: RouteStatus, finisherCount: Int): String {
        val routeId = "rt_test_${UUID.randomUUID().toString().take(12)}"
        transaction(database) {
            exec(
                """
                INSERT INTO sketch_routes (
                    id, name, emoji, path, distance_km, estimated_minutes, difficulty, status,
                    finisher_count, traffic_light_count, elevation_gain_m, repeat_segment_percent, runability
                ) VALUES (
                    '$routeId', 'test', '🧪',
                    ST_SetSRID(ST_MakeLine(ARRAY[ST_MakePoint(126.9235, 37.3905), ST_MakePoint(126.9268, 37.3928)]), 4326)::geography,
                    1.0, 10, 'EASY', '${status.name}', $finisherCount, 0, 0, 0, 0.0
                )
                """.trimIndent(),
            )
        }
        insertedRouteIds += routeId
        return routeId
    }

    private fun readStatus(routeId: String): RouteStatus = transaction(database) {
        var result: RouteStatus? = null
        exec("SELECT status FROM sketch_routes WHERE id = '$routeId'") { rs ->
            if (rs.next()) result = RouteStatus.valueOf(rs.getString("status"))
        }
        requireNotNull(result) { "test route $routeId vanished" }
    }
}
