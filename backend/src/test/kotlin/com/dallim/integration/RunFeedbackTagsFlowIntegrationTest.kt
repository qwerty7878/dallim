package com.dallim.integration

import com.dallim.common.ApiErrorBody
import com.dallim.route.RouteDetailResponse
import com.dallim.run.FeedbackTagsRequest
import com.dallim.run.FeedbackTagsResponse
import com.dallim.run.RunStartResponse
import com.dallim.testsupport.ApiTestSupport.SimpleApiResponse
import com.dallim.testsupport.ApiTestSupport.jsonClient
import com.dallim.testsupport.ApiTestSupport.signupNewUser
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
 * End-to-end API integration tests for POST /runs/{runId}/feedback-tags and its effect on
 * GET /routes/{routeId}'s `topFeedbackTags` — docs/02-api-spec.md 5장/4장,
 * docs/01-feature-spec.md 2.2.G ("코스 평가 태그" — v1.3 문서 301행/395행/483~484행 근거).
 *
 * Runs against a REAL local Postgres/PostGIS (docker-compose, see scripts/dev-db.sh) — no
 * mocks. A run does NOT need to reach COMPLETED to accept feedback tags (SPEC places no such
 * constraint on this endpoint), so these tests submit tags right after POST /runs without ever
 * calling gps-batch/finish, keeping each test focused on the tag-vocabulary/idempotency contract
 * rather than re-deriving RunJudgementService outcomes (already covered by RunFlowIntegrationTest
 * and RunJudgementServiceTest).
 *
 * Each test that asserts on GET /routes/{routeId}'s aggregate uses its own dedicated seeded route
 * id (rt_003/rt_004, see V2__seed_curated_routes.sql) that no other test in this file touches —
 * this table only ever grows from this suite's own tag submissions, so containment/no-growth
 * assertions stay valid across repeated local test runs against the same persistent dev DB.
 */
class RunFeedbackTagsFlowIntegrationTest {

    @Serializable
    private data class RunStartEnvelope(val success: Boolean, val data: RunStartResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class FeedbackTagsEnvelope(val success: Boolean, val data: FeedbackTagsResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class RouteDetailEnvelope(val success: Boolean, val data: RouteDetailResponse? = null, val error: ApiErrorBody? = null)

    private suspend fun startRun(client: HttpClient, token: String, routeId: String): String {
        val response = client.post("/v1/runs") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"routeId":"$routeId","mode":"SOLO","startedAt":"2026-09-07T00:00:00Z"}""")
        }
        val body: RunStartEnvelope = response.body()
        return requireNotNull(body.data) { "startRun failed: ${body.error}" }.runId
    }

    private suspend fun submitTags(client: HttpClient, token: String, runId: String, tags: List<String>) =
        client.post("/v1/runs/$runId/feedback-tags") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(FeedbackTagsRequest(tags))
        }

    // -----------------------------------------------------------------
    // Happy path: submission is reflected in GET /routes/{routeId}.topFeedbackTags
    // -----------------------------------------------------------------

    @Test
    fun `submit feedback tags, then GET routes detail includes it in topFeedbackTags`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        val routeId = "rt_003"
        val runId = startRun(client, token, routeId)

        val response = submitTags(client, token, runId, listOf("그림이 잘 보여요", "평지예요"))
        assertEquals(HttpStatusCode.OK, response.status)
        val body: FeedbackTagsEnvelope = response.body()
        assertEquals(setOf("그림이 잘 보여요", "평지예요"), body.data!!.tags.toSet())

        val detailResponse = client.get("/v1/routes/$routeId")
        assertEquals(HttpStatusCode.OK, detailResponse.status)
        val detailBody: RouteDetailEnvelope = detailResponse.body()
        assertTrue(
            "그림이 잘 보여요" in detailBody.data!!.topFeedbackTags,
            "expected the just-submitted tag to appear in topFeedbackTags, got ${detailBody.data.topFeedbackTags}",
        )
    }

    // -----------------------------------------------------------------
    // Idempotency: a second submission for the same run is a no-op
    // -----------------------------------------------------------------

    @Test
    fun `resubmitting feedback tags for the same run is idempotent and does not change the stored set`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        val runId = startRun(client, token, "rt_004")

        val first = submitTags(client, token, runId, listOf("신호가 적어요"))
        assertEquals(HttpStatusCode.OK, first.status)
        val firstBody: FeedbackTagsEnvelope = first.body()
        assertEquals(listOf("신호가 적어요"), firstBody.data!!.tags)

        // Deliberately different tags on the second call -- must be entirely ignored.
        val second = submitTags(client, token, runId, listOf("경치가 좋아요", "가로등이 밝아요"))
        assertEquals(HttpStatusCode.OK, second.status)
        val secondBody: FeedbackTagsEnvelope = second.body()
        assertEquals(
            listOf("신호가 적어요"),
            secondBody.data!!.tags,
            "a resubmission must return the run's original stored tags unchanged, never the new ones",
        )
    }

    @Test
    fun `submitting an empty tag list is a no-op success and does not block a later real submission`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        val runId = startRun(client, token, "rt_001")

        val empty = submitTags(client, token, runId, emptyList())
        assertEquals(HttpStatusCode.OK, empty.status)
        val emptyBody: FeedbackTagsEnvelope = empty.body()
        assertTrue(emptyBody.data!!.tags.isEmpty())

        val real = submitTags(client, token, runId, listOf("달리기 편해요"))
        assertEquals(HttpStatusCode.OK, real.status)
        val realBody: FeedbackTagsEnvelope = real.body()
        assertEquals(listOf("달리기 편해요"), realBody.data!!.tags)
    }

    // -----------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------

    @Test
    fun `a tag outside the allowed vocabulary returns 400 VALIDATION_ERROR`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        val runId = startRun(client, token, "rt_001")

        val response = submitTags(client, token, runId, listOf("최악이에요"))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error: SimpleApiResponse = response.body()
        assertEquals("VALIDATION_ERROR", error.error?.code)
    }

    @Test
    fun `more than 3 tags returns 400 VALIDATION_ERROR`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        val runId = startRun(client, token, "rt_001")

        val response = submitTags(
            client,
            token,
            runId,
            listOf("그림이 잘 보여요", "달리기 편해요", "신호가 적어요", "평지예요"),
        )
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error: SimpleApiResponse = response.body()
        assertEquals("VALIDATION_ERROR", error.error?.code)
    }

    // -----------------------------------------------------------------
    // Ownership / existence
    // -----------------------------------------------------------------

    @Test
    fun `submitting for another user's run returns 404 RUN_NOT_FOUND`() = testApplication {
        val client = jsonClient()
        val (_, ownerToken) = client.signupNewUser()
        val runId = startRun(client, ownerToken, "rt_001")

        val (_, otherToken) = client.signupNewUser()
        val response = submitTags(client, otherToken, runId, listOf("평지예요"))
        assertEquals(HttpStatusCode.NotFound, response.status)
        val error: SimpleApiResponse = response.body()
        assertEquals("RUN_NOT_FOUND", error.error?.code)
    }

    @Test
    fun `submitting for a nonexistent run returns 404 RUN_NOT_FOUND`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = submitTags(client, token, "run_totally_bogus", listOf("평지예요"))
        assertEquals(HttpStatusCode.NotFound, response.status)
        val error: SimpleApiResponse = response.body()
        assertEquals("RUN_NOT_FOUND", error.error?.code)
    }

    // -----------------------------------------------------------------
    // Auth
    // -----------------------------------------------------------------

    @Test
    fun `feedback-tags without a bearer token returns 401`() = testApplication {
        val client = jsonClient()
        val response = client.post("/v1/runs/run_doesnotexist/feedback-tags") {
            contentType(ContentType.Application.Json)
            setBody(FeedbackTagsRequest(listOf("평지예요")))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `feedback-tags response and route detail never leak a gender field`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        val runId = startRun(client, token, "rt_001")

        val response = submitTags(client, token, runId, listOf("평지예요"))
        assertFalse(response.bodyAsText().contains("gender"))
    }
}
