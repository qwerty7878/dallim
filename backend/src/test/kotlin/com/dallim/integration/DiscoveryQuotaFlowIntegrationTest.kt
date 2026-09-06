package com.dallim.integration

import com.dallim.common.ApiErrorBody
import com.dallim.discovery.DiscoveryQuotaStatus
import com.dallim.discovery.DiscoveryResponse
import com.dallim.testsupport.ApiTestSupport.authGet
import com.dallim.testsupport.ApiTestSupport.jsonClient
import com.dallim.testsupport.ApiTestSupport.signupNewUser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
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
import kotlin.test.assertTrue

/**
 * End-to-end API integration tests for the AI-discovery daily quota (v1.3 문서 S-12/13,
 * docs/02-api-spec.md 8.4 — "무료 3회/일 · 광고 리워드 +2회") against a REAL local
 * Postgres/PostGIS + Redis + OSRM (docker-compose, see scripts/dev-db.sh) — no mocks.
 *
 * Covers: POST /routes/discovery now requires auth (401 without a token, same pattern as
 * com.dallim.run.RunRoutes), free-quota exhaustion (403 DISCOVERY_QUOTA_EXCEEDED on the 4th call
 * of the day), reward-unlock restoring generation ability, GET /routes/discovery/quota's response
 * shape, and the regression check that POST /routes/draw-convert stays anonymous.
 */
class DiscoveryQuotaFlowIntegrationTest {

    // Somewhere in the OSRM foot-network coverage area used elsewhere in the test suite (see
    // RunFlowIntegrationTest / the seeded curated routes) so `/routes/discovery` reliably finds a
    // real route instead of hitting 422 DISCOVERY_NO_ROUTE.
    private val startLat = 37.3905
    private val startLng = 126.9235

    @Serializable
    private data class DiscoveryEnvelope(val success: Boolean, val data: DiscoveryResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class QuotaEnvelope(val success: Boolean, val data: DiscoveryQuotaStatus? = null, val error: ApiErrorBody? = null)

    private suspend fun postDiscovery(client: HttpClient, token: String) = client.post("/v1/routes/discovery") {
        header("Authorization", "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody("""{"startLat":$startLat,"startLng":$startLng,"targetDistanceKm":3.0}""")
    }

    private suspend fun rewardUnlock(client: HttpClient, token: String) = client.post("/v1/routes/discovery/reward-unlock") {
        header("Authorization", "Bearer $token")
    }

    @Test
    fun `POST discovery without a token returns 401`() = testApplication {
        val client = jsonClient()
        val response = client.post("/v1/routes/discovery") {
            contentType(ContentType.Application.Json)
            setBody("""{"startLat":$startLat,"startLng":$startLng,"targetDistanceKm":3.0}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET discovery quota and POST discovery reward-unlock without a token both return 401`() = testApplication {
        val client = jsonClient()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/routes/discovery/quota").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/v1/routes/discovery/reward-unlock").status)
    }

    @Test
    fun `POST draw-convert stays anonymous (regression check)`() = testApplication {
        val client = jsonClient()
        val response = client.post("/v1/routes/draw-convert") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"drawnPath":{"type":"LineString","coordinates":[[126.9235,37.3905]]}}""",
            )
        }
        // Not 401 -- an anonymous caller is allowed to reach the handler at all (it separately
        // 400s as DRAW_TOO_SHORT for this single-point body, which is the point: it got past auth).
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET discovery quota starts at 0 used, limit 3, remaining 3 for a brand-new user`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = client.authGet("/v1/routes/discovery/quota", token)
        assertEquals(HttpStatusCode.OK, response.status)
        val body: QuotaEnvelope = response.body()
        assertEquals(DiscoveryQuotaStatus(usedToday = 0, limit = 3, remainingToday = 3), body.data)
    }

    @Test
    fun `free 3-per-day quota exhausts on the 4th call with 403 DISCOVERY_QUOTA_EXCEEDED, then reward-unlock allows more`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        // 3 free generations succeed, remainingToday counts down 2, 1, 0.
        for (expectedRemaining in listOf(2, 1, 0)) {
            val response = postDiscovery(client, token)
            assertEquals(HttpStatusCode.OK, response.status, "expected generation #${3 - expectedRemaining} to succeed")
            val body: DiscoveryEnvelope = response.body()
            assertEquals(expectedRemaining, body.data!!.remainingToday)
        }

        // 4th call: quota exhausted, no OSRM call needed, 403.
        val fourth = postDiscovery(client, token)
        assertEquals(HttpStatusCode.Forbidden, fourth.status)
        val fourthBody: DiscoveryEnvelope = fourth.body()
        assertEquals("DISCOVERY_QUOTA_EXCEEDED", fourthBody.error?.code)

        // quota reflects the exhausted state.
        val quotaAfterExhaustion: QuotaEnvelope = client.authGet("/v1/routes/discovery/quota", token).body()
        assertEquals(DiscoveryQuotaStatus(usedToday = 3, limit = 3, remainingToday = 0), quotaAfterExhaustion.data)

        // Watch a rewarded ad -- +2 bonus generations.
        val rewardResponse = rewardUnlock(client, token)
        assertEquals(HttpStatusCode.OK, rewardResponse.status)
        val rewardBody: QuotaEnvelope = rewardResponse.body()
        assertEquals(DiscoveryQuotaStatus(usedToday = 3, limit = 5, remainingToday = 2), rewardBody.data)

        // Generation works again, drawing down the bonus.
        val fifth = postDiscovery(client, token)
        assertEquals(HttpStatusCode.OK, fifth.status)
        val fifthBody: DiscoveryEnvelope = fifth.body()
        assertEquals(1, fifthBody.data!!.remainingToday)

        // No artificial cap on reward-unlock itself (docs/02-api-spec.md 8.4) -- calling it again
        // the same day succeeds and grants another +2.
        val secondReward = rewardUnlock(client, token)
        assertEquals(HttpStatusCode.OK, secondReward.status)
        val secondRewardBody: QuotaEnvelope = secondReward.body()
        assertEquals(DiscoveryQuotaStatus(usedToday = 4, limit = 7, remainingToday = 3), secondRewardBody.data)
    }

    @Test
    fun `two different users each get their own independent daily quota`() = testApplication {
        val client = jsonClient()
        val (_, tokenA) = client.signupNewUser()
        val (_, tokenB) = client.signupNewUser()

        // Exhaust user A's free quota.
        repeat(3) { assertEquals(HttpStatusCode.OK, postDiscovery(client, tokenA).status) }
        assertEquals(HttpStatusCode.Forbidden, postDiscovery(client, tokenA).status)

        // User B is unaffected -- still has all 3 free generations.
        val quotaB: QuotaEnvelope = client.authGet("/v1/routes/discovery/quota", tokenB).body()
        assertEquals(DiscoveryQuotaStatus(usedToday = 0, limit = 3, remainingToday = 3), quotaB.data)
        assertEquals(HttpStatusCode.OK, postDiscovery(client, tokenB).status)
    }

    @Test
    fun `a validation failure (bad mode) does not consume quota`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val badRequest = client.post("/v1/routes/discovery") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"startLat":$startLat,"startLng":$startLng,"targetDistanceKm":3.0,"mode":"NOT_A_MODE"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, badRequest.status)

        val quota: QuotaEnvelope = client.authGet("/v1/routes/discovery/quota", token).body()
        assertEquals(DiscoveryQuotaStatus(usedToday = 0, limit = 3, remainingToday = 3), quota.data, "a 400 before generation must not spend a day's quota")
        assertTrue(quota.data!!.remainingToday == 3)
    }
}
