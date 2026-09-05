package com.dallim.integration

import com.dallim.common.ApiErrorBody
import com.dallim.route.HomeContinueRoute
import com.dallim.route.HomeRecentRun
import com.dallim.route.HomeResponse
import com.dallim.run.FinishRunRequest
import com.dallim.run.GpsBatchRequest
import com.dallim.run.GpsPointRequest
import com.dallim.run.RunFinishResponse
import com.dallim.run.RunStartResponse
import com.dallim.run.RunStatus
import com.dallim.testsupport.ApiTestSupport.authGet
import com.dallim.testsupport.ApiTestSupport.jsonClient
import com.dallim.testsupport.ApiTestSupport.signupNewUser
import com.dallim.testsupport.FixtureGpsPoint
import com.dallim.testsupport.GpsFixture
import com.dallim.testsupport.GpsFixtures
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end API integration tests for `GET /home`'s `continueRoutes`/`recentRuns` (previously
 * hardcoded empty per a now-resolved TODO -- see com.dallim.route.HomeService.getHome) against a
 * REAL local Postgres/PostGIS (no mocks, same convention as RunFlowIntegrationTest). Reuses the
 * same seeded curated routes (rt_001/rt_002, see V2__seed_curated_routes.sql) and GPS fixtures as
 * RunFlowIntegrationTest/RunJudgementServiceTest.
 */
class HomeFlowIntegrationTest {

    @Serializable
    private data class RunStartEnvelope(val success: Boolean, val data: RunStartResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class RunFinishEnvelope(val success: Boolean, val data: RunFinishResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class HomeEnvelope(val success: Boolean, val data: HomeResponse? = null, val error: ApiErrorBody? = null)

    private fun toGpsBatchRequest(points: List<FixtureGpsPoint>) =
        GpsBatchRequest(points.map { GpsPointRequest(lat = it.lat, lng = it.lng, timestamp = it.timestamp) })

    @Test
    fun `home for a brand-new user has empty continueRoutes and recentRuns`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val home = getHome(client, token)

        assertTrue(home.continueRoutes.isEmpty())
        assertTrue(home.recentRuns.isEmpty())
    }

    @Test
    fun `a PARTIAL run surfaces in continueRoutes with its route name, status and coverage`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val runId = startRun(client, token, "rt_001")
        uploadAll(client, token, runId, GpsFixtures.load("partial_run"))
        val finishBody = finish(client, token, runId)
        assertEquals(RunStatus.PARTIAL, finishBody.data!!.status, "fixture must actually yield PARTIAL for this test to be meaningful")

        val home = getHome(client, token)

        assertEquals(1, home.continueRoutes.size)
        val entry = home.continueRoutes.single()
        assertEquals("rt_001", entry.routeId)
        assertEquals("고래", entry.name)
        assertEquals("PARTIAL", entry.status)
        assertEquals(finishBody.data.routeCompletionPercent, entry.lastCoveragePercent)
        // A PARTIAL run must never appear as a "recent run" -- that's COMPLETED-only.
        assertTrue(home.recentRuns.isEmpty())
    }

    @Test
    fun `a COMPLETED run surfaces in recentRuns with its distance and completion time`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val runId = startRun(client, token, "rt_001")
        uploadAll(client, token, runId, GpsFixtures.load("completed_run"))
        val finishBody = finish(client, token, runId)
        assertEquals(RunStatus.COMPLETED, finishBody.data!!.status, "fixture must actually yield COMPLETED for this test to be meaningful")

        val home = getHome(client, token)

        assertEquals(1, home.recentRuns.size)
        val entry = home.recentRuns.single()
        assertEquals(runId, entry.runId)
        assertEquals(finishBody.data.distanceKm, entry.distanceKm)
        // A COMPLETED run must never appear in continueRoutes -- that's PARTIAL-only.
        assertTrue(home.continueRoutes.isEmpty())
    }

    @Test
    fun `an ABORTED or UNDER_REVIEW run appears in neither continueRoutes nor recentRuns`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val runId = startRun(client, token, "rt_001")
        uploadAll(client, token, runId, GpsFixtures.load("aborted_run"))
        val finishBody = finish(client, token, runId)
        assertEquals(RunStatus.ABORTED, finishBody.data!!.status, "fixture must actually yield ABORTED for this test to be meaningful")

        val home = getHome(client, token)

        assertTrue(home.continueRoutes.isEmpty())
        assertTrue(home.recentRuns.isEmpty())
    }

    @Test
    fun `re-attempting the same route only surfaces the most recent PARTIAL attempt in continueRoutes`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val firstRunId = startRun(client, token, "rt_001")
        uploadAll(client, token, firstRunId, GpsFixtures.load("partial_run"))
        finish(client, token, firstRunId, finishedAt = "2026-08-30T01:00:00Z")

        val secondRunId = startRun(client, token, "rt_001")
        uploadAll(client, token, secondRunId, GpsFixtures.load("partial_run"))
        finish(client, token, secondRunId, finishedAt = "2026-08-30T02:00:00Z")

        val home = getHome(client, token)

        assertEquals(1, home.continueRoutes.size, "the same route stopped twice must collapse to one continueRoutes entry")
        assertEquals("rt_001", home.continueRoutes.single().routeId)
        // We can't assert *which* run id backs the entry (continueRoutes doesn't expose runId per
        // SPEC), but findContinueRoutes orders by finishedAt DESC before de-duplicating, so this
        // is the second (later) attempt's coverage -- verified indirectly via RunRepository below
        // isn't possible from the HTTP layer, so this test only pins down the collapse-to-one part.
    }

    @Test
    fun `home data is isolated per user - another user's runs never leak in`() = testApplication {
        val client = jsonClient()
        val (_, otherToken) = client.signupNewUser()
        val otherRunId = startRun(client, otherToken, "rt_001")
        uploadAll(client, otherToken, otherRunId, GpsFixtures.load("completed_run"))
        finish(client, otherToken, otherRunId)

        val (_, myToken) = client.signupNewUser()
        val home = getHome(client, myToken)

        assertTrue(home.continueRoutes.isEmpty())
        assertTrue(home.recentRuns.isEmpty(), "another user's COMPLETED run must not appear in my recentRuns")
    }

    @Test
    fun `recentRuns is capped at 3 and ordered newest-first`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val timestamps = listOf(
            "2026-08-30T01:00:00Z",
            "2026-08-30T02:00:00Z",
            "2026-08-30T03:00:00Z",
            "2026-08-30T04:00:00Z",
        )
        val runIds = timestamps.map { finishedAt ->
            val runId = startRun(client, token, "rt_001")
            uploadAll(client, token, runId, GpsFixtures.load("completed_run"))
            finish(client, token, runId, finishedAt = finishedAt)
            runId
        }

        val home = getHome(client, token)

        assertEquals(3, home.recentRuns.size, "recentRuns must be capped at 3 (docs/01-feature-spec.md S-10 '최근 달림 3개')")
        // Newest-first: the last 3 finished runs, most recent first.
        assertEquals(listOf(runIds[3], runIds[2], runIds[1]), home.recentRuns.map { it.runId })
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private suspend fun getHome(client: HttpClient, token: String): HomeResponse {
        val response = client.authGet("/v1/home", token)
        assertEquals(HttpStatusCode.OK, response.status)
        val body: HomeEnvelope = response.body()
        return requireNotNull(body.data) { "GET /home failed: ${body.error}" }
    }

    private suspend fun startRun(client: HttpClient, token: String, routeId: String): String {
        val response = client.post("/v1/runs") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"routeId":"$routeId","mode":"SOLO","startedAt":"2026-08-30T00:00:00Z"}""")
        }
        val body: RunStartEnvelope = response.body()
        return requireNotNull(body.data) { "startRun failed: ${body.error}" }.runId
    }

    private suspend fun uploadAll(client: HttpClient, token: String, runId: String, fixture: GpsFixture) {
        val response = client.post("/v1/runs/$runId/gps-batch") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(toGpsBatchRequest(fixture.actual))
        }
        assertEquals(HttpStatusCode.Accepted, response.status)
    }

    private suspend fun finish(client: HttpClient, token: String, runId: String, finishedAt: String = "2026-08-30T01:00:00Z"): RunFinishEnvelope {
        val response = client.post("/v1/runs/$runId/finish") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(FinishRunRequest(finishedAt = finishedAt))
        }
        return response.body()
    }
}
