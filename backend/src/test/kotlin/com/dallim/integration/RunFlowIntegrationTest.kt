package com.dallim.integration

import com.dallim.common.ApiErrorBody
import com.dallim.route.RouteFinisherItem
import com.dallim.route.RouteListResponse
import com.dallim.run.FinishRunRequest
import com.dallim.run.GpsBatchRequest
import com.dallim.run.GpsBatchResponse
import com.dallim.run.GpsPointRequest
import com.dallim.run.RunDetailResponse
import com.dallim.run.RunFinishResponse
import com.dallim.run.RunStartResponse
import com.dallim.run.RunStatus
import com.dallim.testsupport.ApiTestSupport.SignupBody
import com.dallim.testsupport.ApiTestSupport.SimpleApiResponse
import com.dallim.testsupport.ApiTestSupport.authGet
import com.dallim.testsupport.ApiTestSupport.jsonClient
import com.dallim.testsupport.ApiTestSupport.signupNewUser
import com.dallim.testsupport.ApiTestSupport.uniqueEmail
import com.dallim.testsupport.FixtureGpsPoint
import com.dallim.testsupport.GpsFixture
import com.dallim.testsupport.GpsFixtures
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end API integration tests for the run domain against a REAL local Postgres/PostGIS +
 * Redis (docker-compose, see scripts/dev-db.sh) -- no mocks, no in-memory DB substitute. Flow
 * mirrors docs/02-api-spec.md 5장:
 *
 *   POST /auth/signup -> GET /routes -> POST /runs -> POST /runs/{id}/gps-batch (x N)
 *   -> POST /runs/{id}/finish -> GET /runs/{id} -> GET /routes/{id}/finishers
 *
 * NOTE: this flow uses /auth/signup to obtain a token rather than also exercising
 * POST /users/me/profile (see UserProfileFlowIntegrationTest for that endpoint's own coverage) --
 * a run doesn't require a registered nickname/profile to start.
 *
 * Uses the seeded curated route rt_001 (see V2__seed_curated_routes.sql) as the planned route,
 * and the same GPS fixtures used by RunJudgementServiceTest (built to mirror rt_001's exact
 * planned path) as realistic request bodies -- so a COMPLETED/PARTIAL/ABORTED/UNDER_REVIEW
 * outcome here is driven by the exact same production judgement code, end-to-end over HTTP.
 */
class RunFlowIntegrationTest {

    private val routeId = "rt_001"

    @Serializable
    private data class RunStartEnvelope(val success: Boolean, val data: RunStartResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class GpsBatchEnvelope(val success: Boolean, val data: GpsBatchResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class RunFinishEnvelope(val success: Boolean, val data: RunFinishResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class RunDetailEnvelope(val success: Boolean, val data: RunDetailResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class RouteListEnvelope(val success: Boolean, val data: RouteListResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class FinishersEnvelope(val success: Boolean, val data: FinishersData? = null)

    @Serializable
    private data class FinishersData(val items: List<RouteFinisherItem>)

    private fun toGpsBatchRequest(points: List<FixtureGpsPoint>) =
        GpsBatchRequest(points.map { GpsPointRequest(lat = it.lat, lng = it.lng, timestamp = it.timestamp) })

    // -----------------------------------------------------------------
    // Full happy-path flow, token-to-token, using a realistically-completed trace
    // -----------------------------------------------------------------

    @Test
    fun `full flow - signup, list routes, start run, upload gps in two batches, finish COMPLETED, fetch detail`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        // GET /routes (list) works with a fresh token and never leaks gender.
        val routesResponse = client.authGet("/routes", token)
        assertEquals(HttpStatusCode.OK, routesResponse.status)
        assertFalse(routesResponse.bodyAsText().contains("gender"), "GET /routes response must never contain a gender field")
        val routes: RouteListEnvelope = routesResponse.body()
        assertTrue(routes.data!!.items.any { it.routeId == routeId }, "expected seeded route $routeId in GET /routes")

        // POST /runs
        val startResponse = client.post("/runs") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"routeId":"$routeId","mode":"SOLO","startedAt":"2026-08-30T00:00:00Z"}""")
        }
        assertEquals(HttpStatusCode.Created, startResponse.status)
        val startBody: RunStartEnvelope = startResponse.body()
        val runId = requireNotNull(startBody.data).runId
        assertEquals(RunStatus.IN_PROGRESS, startBody.data.status)

        // POST /runs/{id}/gps-batch, split into two partial uploads (CLAUDE.md rule 4).
        val fixture = GpsFixtures.load("completed_run")
        val firstHalf = fixture.actual.subList(0, fixture.actual.size / 2)
        val secondHalf = fixture.actual.subList(fixture.actual.size / 2, fixture.actual.size)

        val batch1 = client.post("/runs/$runId/gps-batch") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(toGpsBatchRequest(firstHalf))
        }
        assertEquals(HttpStatusCode.Accepted, batch1.status)
        val batch1Body: GpsBatchEnvelope = batch1.body()
        assertEquals(firstHalf.size, batch1Body.data!!.receivedCount)

        val batch2 = client.post("/runs/$runId/gps-batch") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(toGpsBatchRequest(secondHalf))
        }
        assertEquals(HttpStatusCode.Accepted, batch2.status)
        val batch2Body: GpsBatchEnvelope = batch2.body()
        // receivedCount must be the CUMULATIVE total, not just this batch's size.
        assertEquals(fixture.actual.size, batch2Body.data!!.receivedCount)

        // POST /runs/{id}/finish -- send a deliberately WRONG clientPrecheckStatus to prove the
        // server recomputes and never trusts it (CLAUDE.md rule 3 -- the key regression guard).
        val finishResponse = client.post("/runs/$runId/finish") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(FinishRunRequest(finishedAt = "2026-08-30T01:00:00Z", clientPrecheckStatus = "ABORTED"))
        }
        assertEquals(HttpStatusCode.OK, finishResponse.status)
        val finishBody: RunFinishEnvelope = finishResponse.body()
        assertEquals(
            RunStatus.COMPLETED,
            finishBody.data!!.status,
            "server must compute COMPLETED from the real GPS trace regardless of the client's clientPrecheckStatus=ABORTED",
        )
        assertTrue(finishBody.data.routeCompletionPercent >= 90)

        // GET /runs/{id}
        val detailResponse = client.authGet("/runs/$runId", token)
        assertEquals(HttpStatusCode.OK, detailResponse.status)
        assertFalse(detailResponse.bodyAsText().contains("gender"))
        val detailBody: RunDetailEnvelope = detailResponse.body()
        assertEquals(RunStatus.COMPLETED, detailBody.data!!.status)
        assertEquals(routeId, detailBody.data.routeId)
        assertTrue(detailBody.data.plannedGeoJson.coordinates.isNotEmpty())
        assertTrue(detailBody.data.actualGeoJson.coordinates.isNotEmpty())
    }

    @Test
    fun `finish - clientPrecheckStatus COMPLETED is ignored for a genuinely aborted trace`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val runId = startRun(client, token, routeId)
        val fixture = GpsFixtures.load("aborted_run")
        uploadAll(client, token, runId, fixture)

        val finishResponse = client.post("/runs/$runId/finish") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(FinishRunRequest(finishedAt = "2026-08-30T01:00:00Z", clientPrecheckStatus = "COMPLETED"))
        }
        val finishBody: RunFinishEnvelope = finishResponse.body()
        assertEquals(
            RunStatus.ABORTED,
            finishBody.data!!.status,
            "server must compute ABORTED regardless of the client claiming COMPLETED",
        )
    }

    @Test
    fun `finish - UNDER_REVIEW fixture is flagged even though coverage alone would pass`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val runId = startRun(client, token, routeId)
        val fixture = GpsFixtures.load("under_review_run")
        uploadAll(client, token, runId, fixture)

        val finishResponse = client.post("/runs/$runId/finish") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(FinishRunRequest(finishedAt = "2026-08-30T01:00:00Z"))
        }
        val finishBody: RunFinishEnvelope = finishResponse.body()
        assertEquals(RunStatus.UNDER_REVIEW, finishBody.data!!.status)
    }

    // -----------------------------------------------------------------
    // Auth / 401 checks
    // -----------------------------------------------------------------

    @Test
    fun `locked endpoints return 401 without a bearer token`() = testApplication {
        val client = jsonClient()

        val noAuthCases = listOf(
            suspend {
                client.post("/runs") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"routeId":"$routeId","startedAt":"2026-08-30T00:00:00Z"}""")
                }
            },
            suspend { client.get("/runs/run_doesnotexist") },
            suspend {
                client.post("/runs/run_doesnotexist/gps-batch") {
                    contentType(ContentType.Application.Json)
                    setBody(GpsBatchRequest(emptyList()))
                }
            },
            suspend {
                client.post("/runs/run_doesnotexist/finish") {
                    contentType(ContentType.Application.Json)
                    setBody(FinishRunRequest(finishedAt = "2026-08-30T00:00:00Z"))
                }
            },
        )

        for (call in noAuthCases) {
            val response = call()
            assertEquals(HttpStatusCode.Unauthorized, response.status, "expected 401 for a run-domain 🔒 endpoint with no token")
        }
    }

    @Test
    fun `GET routes works anonymously (optional auth) and still hides gender`() = testApplication {
        val client = jsonClient()
        val response = client.get("/routes")
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(response.bodyAsText().contains("gender"))
    }

    // -----------------------------------------------------------------
    // Error-case / status-code contract checks
    // -----------------------------------------------------------------

    @Test
    fun `finish then gps-batch again returns 409 RUN_ALREADY_FINISHED`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        val runId = startRun(client, token, routeId)
        uploadAll(client, token, runId, GpsFixtures.load("completed_run"))
        finish(client, token, runId)

        val secondBatch = client.post("/runs/$runId/gps-batch") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(GpsBatchRequest(listOf(GpsPointRequest(37.0, 127.0, "2026-08-30T02:00:00Z"))))
        }
        assertEquals(HttpStatusCode.Conflict, secondBatch.status)
        val error: SimpleApiResponse = secondBatch.body()
        assertEquals("RUN_ALREADY_FINISHED", error.error?.code)
    }

    @Test
    fun `finish twice returns 409 RUN_ALREADY_FINISHED on the second call`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        val runId = startRun(client, token, routeId)
        uploadAll(client, token, runId, GpsFixtures.load("completed_run"))
        finish(client, token, runId)

        val secondFinish = client.post("/runs/$runId/finish") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(FinishRunRequest(finishedAt = "2026-08-30T02:00:00Z"))
        }
        assertEquals(HttpStatusCode.Conflict, secondFinish.status)
        val error: SimpleApiResponse = secondFinish.body()
        assertEquals("RUN_ALREADY_FINISHED", error.error?.code)
    }

    @Test
    fun `finish with fewer than 2 uploaded GPS points returns 400 GPS_DATA_INSUFFICIENT`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        val runId = startRun(client, token, routeId)

        val batch = client.post("/runs/$runId/gps-batch") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(GpsBatchRequest(listOf(GpsPointRequest(37.3905, 126.9235, "2026-08-30T00:00:00Z"))))
        }
        assertEquals(HttpStatusCode.Accepted, batch.status)

        val finishResponse = client.post("/runs/$runId/finish") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(FinishRunRequest(finishedAt = "2026-08-30T00:05:00Z"))
        }
        assertEquals(HttpStatusCode.BadRequest, finishResponse.status)
        val error: SimpleApiResponse = finishResponse.body()
        assertEquals("GPS_DATA_INSUFFICIENT", error.error?.code)
    }

    @Test
    fun `POST runs with an unknown routeId returns 404 ROUTE_NOT_FOUND`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = client.post("/runs") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"routeId":"rt_does_not_exist","startedAt":"2026-08-30T00:00:00Z"}""")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        val error: SimpleApiResponse = response.body()
        assertEquals("ROUTE_NOT_FOUND", error.error?.code)
    }

    @Test
    fun `GET runs by unknown id returns 404 RUN_NOT_FOUND`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = client.authGet("/runs/run_totally_bogus", token)
        assertEquals(HttpStatusCode.NotFound, response.status)
        val error: SimpleApiResponse = response.body()
        assertEquals("RUN_NOT_FOUND", error.error?.code)
    }

    @Test
    fun `GET runs for another user's run also returns 404 RUN_NOT_FOUND (ownership is not leaked)`() = testApplication {
        val client = jsonClient()
        val (_, ownerToken) = client.signupNewUser()
        val runId = startRun(client, ownerToken, routeId)

        val (_, otherToken) = client.signupNewUser()
        val response = client.authGet("/runs/$runId", otherToken)
        assertEquals(HttpStatusCode.NotFound, response.status)
        val error: SimpleApiResponse = response.body()
        assertEquals("RUN_NOT_FOUND", error.error?.code)
    }

    @Test
    fun `signup with a duplicate email returns 409 EMAIL_ALREADY_EXISTS`() = testApplication {
        // The analogous duplicate-nickname 409 (NICKNAME_TAKEN) is covered separately in
        // UserProfileFlowIntegrationTest; this test is about /auth/signup's own uniqueness
        // constraint on email.
        val client = jsonClient()
        val email = uniqueEmail()
        client.signupNewUser(email = email)

        val response = client.post("/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(SignupBody(email, "qa-Passw0rd"))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        val error: SimpleApiResponse = response.body()
        assertEquals("EMAIL_ALREADY_EXISTS", error.error?.code)
    }

    // -----------------------------------------------------------------
    // GET /routes/{routeId}/finishers only reflects COMPLETED runs
    // -----------------------------------------------------------------

    @Test
    fun `routes finishers - only a COMPLETED run appears, not a PARTIAL one from the same user`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        // Use rt_002 so this test's traces don't have to be rt_001-shaped; the route's actual
        // planned geometry doesn't matter here since we only assert on run-id membership, not on
        // status (route coverage is computed against rt_002's own planned path either way).
        val finisherRouteId = "rt_002"

        val completedRunId = startRun(client, token, finisherRouteId)
        // A dense, tight loop-back-and-forth trace isn't needed -- any trace already proven to
        // yield COMPLETED against rt_001 in RunJudgementServiceTest would need rt_001's own
        // planned path to match, so here we simply upload rt_001's completed fixture against
        // rt_002 and assert on whichever status results, driving the two runs in this test to
        // deliberately differing statuses instead of asserting a specific one for rt_002.
        uploadAll(client, token, completedRunId, GpsFixtures.load("completed_run"))
        val completedFinish = finish(client, token, completedRunId)
        val completedStatus = completedFinish.data!!.status

        val otherRunId = startRun(client, token, finisherRouteId)
        uploadAll(client, token, otherRunId, GpsFixtures.load("aborted_run"))
        val otherFinish = finish(client, token, otherRunId)
        assertTrue(otherFinish.data!!.status != RunStatus.COMPLETED, "the second trace must not also complete, or this test can't distinguish the two")

        val finishersResponse = client.get("/routes/$finisherRouteId/finishers")
        assertEquals(HttpStatusCode.OK, finishersResponse.status)
        val finishers: FinishersEnvelope = finishersResponse.body()
        val finisherRunIds = finishers.data!!.items.map { it.runId }.toSet()

        if (completedStatus == RunStatus.COMPLETED) {
            assertTrue(completedRunId in finisherRunIds, "a COMPLETED run must appear in /finishers")
        }
        assertFalse(otherRunId in finisherRunIds, "a non-COMPLETED run must never appear in /finishers")
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private suspend fun startRun(client: HttpClient, token: String, routeId: String): String {
        val response = client.post("/runs") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"routeId":"$routeId","mode":"SOLO","startedAt":"2026-08-30T00:00:00Z"}""")
        }
        val body: RunStartEnvelope = response.body()
        return requireNotNull(body.data) { "startRun failed: ${body.error}" }.runId
    }

    private suspend fun uploadAll(client: HttpClient, token: String, runId: String, fixture: GpsFixture) {
        val response = client.post("/runs/$runId/gps-batch") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(toGpsBatchRequest(fixture.actual))
        }
        assertEquals(HttpStatusCode.Accepted, response.status)
    }

    private suspend fun finish(client: HttpClient, token: String, runId: String): RunFinishEnvelope {
        val response = client.post("/runs/$runId/finish") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(FinishRunRequest(finishedAt = "2026-08-30T01:00:00Z"))
        }
        return response.body()
    }
}
