package com.dallim.integration

import com.dallim.common.ApiErrorBody
import com.dallim.route.RouteDetailResponse
import com.dallim.route.ShapeVoteRequest
import com.dallim.route.ShapeVoteSubmitResponse
import com.dallim.testsupport.ApiTestSupport.SimpleApiResponse
import com.dallim.testsupport.ApiTestSupport.jsonClient
import com.dallim.testsupport.ApiTestSupport.signupNewUser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end API integration tests for POST /routes/{routeId}/shape-votes and the
 * `shapeVotes`/`myShapeVote` fields on GET /routes/{routeId} -- docs/02-api-spec.md 4장,
 * docs/01-feature-spec.md 2.2.C, docs/달림_화면별_상세기획서_v1.3.md 297행/271행
 * ("커뮤니티 투표(모양 맞추기)").
 *
 * Runs against a REAL local Postgres/PostGIS (docker-compose, see scripts/dev-db.sh) -- no
 * mocks. This is a free-text aggregate SEPARATE from sketch_routes.name/RouteFeedbackTags.
 * Any test that needs an EXACT tally/percentage (not just containment) calls [freshRoute] to
 * insert its own brand-new sketch_routes row directly (same `directDb` raw-SQL pattern as
 * MeetupFlowIntegrationTest) instead of reusing one of the five seeded rt_001..rt_005 rows --
 * those are shared across many other test files/runs forever, so only a route nobody else has
 * ever voted on can support an exact-count assertion. Tests that only assert containment/null-ness
 * (never an exact count) reuse a seeded route id, matching this codebase's existing convention.
 */
class RouteShapeVotesFlowIntegrationTest {

    private val directDb: Database by lazy {
        Database.connect(
            url = "jdbc:postgresql://localhost:5432/dallim",
            driver = "org.postgresql.Driver",
            user = "dallim",
            password = "dallim",
        )
    }

    /** Inserts a brand-new, never-before-voted-on sketch_routes row and returns its id. Geometry
     * is a throwaway 2-point line -- shape votes never touch `path`, so its actual coordinates
     * are irrelevant, only its NOT NULL constraint matters (see V1__init.sql). */
    private fun freshRoute(): String {
        val routeId = "rtx${UUID.randomUUID().toString().take(10)}"
        transaction(directDb) {
            exec(
                """
                INSERT INTO sketch_routes (id, name, emoji, path, distance_km, estimated_minutes, difficulty, status)
                VALUES (
                    '$routeId', '테스트코스', '🧪',
                    ST_SetSRID(ST_MakeLine(ARRAY[ST_MakePoint(127.000, 37.500), ST_MakePoint(127.001, 37.501)]), 4326)::geography,
                    1.0, 10, 'EASY', 'DISCOVERY'
                )
                """.trimIndent(),
            )
        }
        return routeId
    }

    @Serializable
    private data class ShapeVoteEnvelope(val success: Boolean, val data: ShapeVoteSubmitResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class RouteDetailEnvelope(val success: Boolean, val data: RouteDetailResponse? = null, val error: ApiErrorBody? = null)

    private suspend fun vote(client: HttpClient, token: String?, routeId: String, label: String): HttpResponse =
        client.post("/v1/routes/$routeId/shape-votes") {
            token?.let { header("Authorization", "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(ShapeVoteRequest(label))
        }

    private suspend fun detail(client: HttpClient, token: String? = null, routeId: String): RouteDetailResponse {
        val response = client.get("/v1/routes/$routeId") {
            token?.let { header("Authorization", "Bearer $it") }
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body: RouteDetailEnvelope = response.body()
        return requireNotNull(body.data) { "GET /routes/$routeId failed: ${body.error}" }
    }

    // -----------------------------------------------------------------
    // Happy path: first vote creates a new candidate
    // -----------------------------------------------------------------

    @Test
    fun `first vote for a route creates a new candidate at 100 percent`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        val routeId = freshRoute()

        val response = vote(client, token, routeId, "물범")
        assertEquals(HttpStatusCode.OK, response.status)
        val body: ShapeVoteEnvelope = response.body()
        requireNotNull(body.data) { "vote failed: ${body.error}" }
        assertEquals("물범", body.data.myLabel)
        assertEquals(listOf("물범" to 100), body.data.shapeVotes.map { it.label to it.percent })
    }

    // -----------------------------------------------------------------
    // Re-vote replaces rather than accumulates
    // -----------------------------------------------------------------

    @Test
    fun `re-voting replaces the previous label instead of accumulating`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        val routeId = freshRoute()

        val first = vote(client, token, routeId, "별")
        assertEquals(HttpStatusCode.OK, first.status)

        val second = vote(client, token, routeId, "불가사리")
        assertEquals(HttpStatusCode.OK, second.status)
        val secondBody: ShapeVoteEnvelope = second.body()
        val tallies = requireNotNull(secondBody.data).shapeVotes

        assertEquals(1, tallies.size, "the same user's earlier vote must be replaced, not kept alongside the new one")
        assertEquals("불가사리", tallies.single().label)
        assertEquals(100, tallies.single().percent)
    }

    // -----------------------------------------------------------------
    // Multiple users -> aggregate percentages
    // -----------------------------------------------------------------

    @Test
    fun `multiple users' votes are tallied into correct percentages, sorted by count`() = testApplication {
        val client = jsonClient()
        val routeId = freshRoute()
        val (_, tokenA) = client.signupNewUser()
        val (_, tokenB) = client.signupNewUser()
        val (_, tokenC) = client.signupNewUser()

        vote(client, tokenA, routeId, "고양이")
        vote(client, tokenB, routeId, "고양이")
        val response = vote(client, tokenC, routeId, "강아지")
        assertEquals(HttpStatusCode.OK, response.status)
        val body: ShapeVoteEnvelope = response.body()
        val tallies = requireNotNull(body.data).shapeVotes

        assertEquals("고양이", tallies.first().label, "the 2-vote label must rank first")
        assertEquals(67, tallies.first { it.label == "고양이" }.percent)
        assertEquals(33, tallies.first { it.label == "강아지" }.percent)
    }

    // -----------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------

    @Test
    fun `blank label returns 400 VALIDATION_ERROR`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = vote(client, token, freshRoute(), "   ")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error: SimpleApiResponse = response.body()
        assertEquals("VALIDATION_ERROR", error.error?.code)
    }

    @Test
    fun `label longer than 10 chars returns 400 VALIDATION_ERROR`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = vote(client, token, freshRoute(), "가나다라마바사아자차카")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error: SimpleApiResponse = response.body()
        assertEquals("VALIDATION_ERROR", error.error?.code)
    }

    // -----------------------------------------------------------------
    // Existence / auth
    // -----------------------------------------------------------------

    @Test
    fun `voting on a nonexistent route returns 404 ROUTE_NOT_FOUND`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = vote(client, token, "rt_totally_bogus", "고래")
        assertEquals(HttpStatusCode.NotFound, response.status)
        val error: SimpleApiResponse = response.body()
        assertEquals("ROUTE_NOT_FOUND", error.error?.code)
    }

    @Test
    fun `voting without a bearer token returns 401`() = testApplication {
        val client = jsonClient()
        val response = vote(client, null, "rt_001", "고래")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // -----------------------------------------------------------------
    // GET /routes/{routeId} exposure
    // -----------------------------------------------------------------

    @Test
    fun `GET route detail exposes myShapeVote null for an anonymous or non-voting caller`() = testApplication {
        val client = jsonClient()
        val routeId = freshRoute()
        val (_, otherToken) = client.signupNewUser()
        vote(client, otherToken, routeId, "토끼모자")

        val (_, myToken) = client.signupNewUser()

        val anonDetail = detail(client, token = null, routeId = routeId)
        assertNull(anonDetail.myShapeVote)

        val myDetail = detail(client, token = myToken, routeId = routeId)
        assertNull(myDetail.myShapeVote, "a logged-in user who never voted must still see null")
    }

    @Test
    fun `GET route detail exposes myShapeVote and shapeVotes after voting`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        val routeId = freshRoute()

        vote(client, token, routeId, "나비모양")

        val body = detail(client, token = token, routeId = routeId)
        assertEquals("나비모양", body.myShapeVote)
        assertTrue(body.shapeVotes.any { it.label == "나비모양" })
    }

    @Test
    fun `shape-vote response never leaks a gender field`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = vote(client, token, freshRoute(), "고래")
        assertFalse(response.bodyAsText().contains("gender"))
    }
}
