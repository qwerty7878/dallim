package com.dallim.integration

import com.dallim.common.ApiErrorBody
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
import com.dallim.user.SavedRoutesResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
 * End-to-end API integration tests for `GET /users/me/saved-routes`' `hasRun` field (previously
 * hardcoded `false` per a now-resolved TODO -- see com.dallim.user.SavedRouteService.listSaved)
 * against a REAL local Postgres/PostGIS (no mocks, same convention as RunFlowIntegrationTest).
 */
class SavedRoutesFlowIntegrationTest {

    @Serializable
    private data class RunStartEnvelope(val success: Boolean, val data: RunStartResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class RunFinishEnvelope(val success: Boolean, val data: RunFinishResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class SavedRoutesEnvelope(val success: Boolean, val data: SavedRoutesResponse? = null, val error: ApiErrorBody? = null)

    private fun toGpsBatchRequest(points: List<FixtureGpsPoint>) =
        GpsBatchRequest(points.map { GpsPointRequest(lat = it.lat, lng = it.lng, timestamp = it.timestamp) })

    @Test
    fun `saved route with no run has hasRun false`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        saveRoute(client, token, "rt_001")

        val items = listSaved(client, token)
        assertEquals(false, items.single { it.routeId == "rt_001" }.hasRun)
    }

    @Test
    fun `saved route with a PARTIAL run still has hasRun false (only COMPLETED counts)`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        saveRoute(client, token, "rt_001")
        val runId = startRun(client, token, "rt_001")
        uploadAll(client, token, runId, GpsFixtures.load("partial_run"))
        val finishBody = finish(client, token, runId)
        assertEquals(RunStatus.PARTIAL, finishBody.data!!.status, "fixture must actually yield PARTIAL for this test to be meaningful")

        val items = listSaved(client, token)
        assertFalse(items.single { it.routeId == "rt_001" }.hasRun)
    }

    @Test
    fun `saved route becomes hasRun true after a COMPLETED run`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        saveRoute(client, token, "rt_001")
        val runId = startRun(client, token, "rt_001")
        uploadAll(client, token, runId, GpsFixtures.load("completed_run"))
        val finishBody = finish(client, token, runId)
        assertEquals(RunStatus.COMPLETED, finishBody.data!!.status, "fixture must actually yield COMPLETED for this test to be meaningful")

        val items = listSaved(client, token)
        assertTrue(items.single { it.routeId == "rt_001" }.hasRun)
    }

    @Test
    fun `hasRun is isolated per user - another user's COMPLETED run does not flip it`() = testApplication {
        val client = jsonClient()
        val (_, otherToken) = client.signupNewUser()
        val otherRunId = startRun(client, otherToken, "rt_001")
        uploadAll(client, otherToken, otherRunId, GpsFixtures.load("completed_run"))
        finish(client, otherToken, otherRunId)

        val (_, myToken) = client.signupNewUser()
        saveRoute(client, myToken, "rt_001")

        val items = listSaved(client, myToken)
        assertFalse(items.single { it.routeId == "rt_001" }.hasRun, "another user's COMPLETED run must not flip my hasRun")
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private suspend fun saveRoute(client: HttpClient, token: String, routeId: String) {
        val response = client.post("/v1/users/me/saved-routes/$routeId") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    private suspend fun listSaved(client: HttpClient, token: String) =
        client.authGet("/v1/users/me/saved-routes", token).let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val body: SavedRoutesEnvelope = response.body()
            requireNotNull(body.data) { "GET /users/me/saved-routes failed: ${body.error}" }.items
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
