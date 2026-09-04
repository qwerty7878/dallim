package com.dallim.integration

import com.dallim.common.ApiErrorBody
import com.dallim.notification.NotificationListResponse
import com.dallim.notification.UnreadCountResponse
import com.dallim.run.FinishRunRequest
import com.dallim.run.GpsBatchRequest
import com.dallim.run.GpsPointRequest
import com.dallim.run.RunFinishResponse
import com.dallim.run.RunStartResponse
import com.dallim.run.RunStatus
import com.dallim.testsupport.ApiTestSupport.jsonClient
import com.dallim.testsupport.ApiTestSupport.signupNewUser
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
 * End-to-end coverage for the notification domain (docs/02-api-spec.md 9장, S-46) against a REAL
 * local Postgres/PostGIS + Redis (docker-compose, see scripts/dev-db.sh) -- same conventions as
 * RunFlowIntegrationTest (no mocks, unique email per test, no assertions on absolute global
 * counts since the DB is shared/persistent).
 *
 * Two things are under test:
 *  1. The 9.4 server-internal trigger -- com.dallim.run.RunService.finishRun creates exactly one
 *     RUN_COMPLETED notification when (and only when) a run resolves to COMPLETED.
 *  2. The three client-facing endpoints (9.1-9.3) themselves: list/pagination, unread-count, and
 *     read (idempotent, ownership-scoped 404).
 */
class NotificationFlowIntegrationTest {

    private val routeId = "rt_001"

    @Serializable
    private data class RunStartEnvelope(val success: Boolean, val data: RunStartResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class RunFinishEnvelope(val success: Boolean, val data: RunFinishResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class NotificationListEnvelope(val success: Boolean, val data: NotificationListResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class UnreadCountEnvelope(val success: Boolean, val data: UnreadCountResponse? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class SimpleEnvelope(val success: Boolean, val error: ApiErrorBody? = null)

    private fun toGpsBatchRequest(points: List<FixtureGpsPoint>) =
        GpsBatchRequest(points.map { GpsPointRequest(lat = it.lat, lng = it.lng, timestamp = it.timestamp) })

    // -----------------------------------------------------------------
    // 9.4 trigger -- fires exactly once on COMPLETED, never on a non-COMPLETED finish
    // -----------------------------------------------------------------

    @Test
    fun `finishRun COMPLETED creates exactly one RUN_COMPLETED notification, and a later non-COMPLETED finish adds none`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        // Run 1: COMPLETED -> exactly one notification.
        val completedRunId = startRun(client, token, routeId)
        uploadAll(client, token, completedRunId, GpsFixtures.load("completed_run"))
        val completedFinish = finish(client, token, completedRunId)
        assertEquals(RunStatus.COMPLETED, completedFinish.data!!.status)

        val listAfterFirst = getNotifications(client, token)
        assertEquals(1, listAfterFirst.data!!.totalCount, "exactly one notification after one COMPLETED run")
        val item = listAfterFirst.data.items.single()
        assertEquals("RUN_COMPLETED", item.type.name)
        assertEquals(completedRunId, item.relatedRunId)
        assertFalse(item.isRead)
        assertTrue(item.body.contains("고래"), "body should mention rt_001's route name ('고래'), was: ${item.body}")
        assertTrue(item.body.contains("km"), "body should mention distance in km, was: ${item.body}")

        val unreadAfterFirst = getUnreadCount(client, token)
        assertEquals(1, unreadAfterFirst.data!!.unreadCount)

        // Run 2: same user, same route, but an ABORTED trace -- must NOT add a second notification.
        val abortedRunId = startRun(client, token, routeId)
        uploadAll(client, token, abortedRunId, GpsFixtures.load("aborted_run"))
        val abortedFinish = finish(client, token, abortedRunId)
        assertEquals(RunStatus.ABORTED, abortedFinish.data!!.status)

        val listAfterSecond = getNotifications(client, token)
        assertEquals(1, listAfterSecond.data!!.totalCount, "a non-COMPLETED finish must not create a notification")
        assertEquals(completedRunId, listAfterSecond.data.items.single().relatedRunId)

        val unreadAfterSecond = getUnreadCount(client, token)
        assertEquals(1, unreadAfterSecond.data!!.unreadCount)
    }

    @Test
    fun `finishRun UNDER_REVIEW does not create a notification either`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val runId = startRun(client, token, routeId)
        uploadAll(client, token, runId, GpsFixtures.load("under_review_run"))
        val finishResult = finish(client, token, runId)
        assertEquals(RunStatus.UNDER_REVIEW, finishResult.data!!.status)

        val list = getNotifications(client, token)
        assertEquals(0, list.data!!.totalCount)
    }

    // -----------------------------------------------------------------
    // 9.1 GET /notifications -- list + pagination
    // -----------------------------------------------------------------

    @Test
    fun `GET notifications paginates newest-first with the routes-style page-size contract`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        // Three independent COMPLETED runs against rt_001 -> three notifications for this user.
        val runIds = (1..3).map { finishCompletedRun(client, token) }

        val defaultPage = getNotifications(client, token)
        assertEquals(3, defaultPage.data!!.totalCount)
        assertEquals(3, defaultPage.data.items.size, "default size=20 should return all 3 in one page")
        assertEquals(0, defaultPage.data.page)
        assertEquals(20, defaultPage.data.size)
        // Newest first.
        assertEquals(runIds.last(), defaultPage.data.items.first().relatedRunId)

        val firstPage = getNotifications(client, token, page = 0, size = 2)
        assertEquals(3, firstPage.data!!.totalCount)
        assertEquals(2, firstPage.data.items.size)

        val secondPage = getNotifications(client, token, page = 1, size = 2)
        assertEquals(3, secondPage.data!!.totalCount)
        assertEquals(1, secondPage.data.items.size)

        val allIds = (firstPage.data.items + secondPage.data.items).map { it.id }
        assertEquals(3, allIds.toSet().size, "paged items must not overlap or repeat")
        assertEquals(runIds.toSet(), (firstPage.data.items + secondPage.data.items).map { it.relatedRunId }.toSet())
    }

    // -----------------------------------------------------------------
    // 9.2 GET /notifications/unread-count
    // -----------------------------------------------------------------

    @Test
    fun `GET unread-count is 0 for a brand-new user with no runs`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val unread = getUnreadCount(client, token)
        assertEquals(0, unread.data!!.unreadCount)
    }

    // -----------------------------------------------------------------
    // 9.3 POST /notifications/{id}/read -- idempotent, ownership-scoped
    // -----------------------------------------------------------------

    @Test
    fun `POST notifications read marks it read and is idempotent on a second call`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        finishCompletedRun(client, token)

        val notificationId = getNotifications(client, token).data!!.items.single().id
        assertEquals(1, getUnreadCount(client, token).data!!.unreadCount)

        val firstRead = client.post("/v1/notifications/$notificationId/read") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, firstRead.status)
        assertEquals(0, getUnreadCount(client, token).data!!.unreadCount)
        assertTrue(getNotifications(client, token).data!!.items.single().isRead)

        // Second call on an already-read notification must still be a 200 no-op (idempotent).
        val secondRead = client.post("/v1/notifications/$notificationId/read") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, secondRead.status)
        assertEquals(0, getUnreadCount(client, token).data!!.unreadCount)
    }

    @Test
    fun `POST notifications read for an unknown id returns 404 NOTIFICATION_NOT_FOUND`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = client.post("/v1/notifications/ntf_totally_bogus/read") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        val error: SimpleEnvelope = response.body()
        assertEquals("NOTIFICATION_NOT_FOUND", error.error?.code)
    }

    @Test
    fun `POST notifications read for another user's notification returns 404 (ownership is not leaked)`() = testApplication {
        val client = jsonClient()
        val (_, ownerToken) = client.signupNewUser()
        finishCompletedRun(client, ownerToken)
        val notificationId = getNotifications(client, ownerToken).data!!.items.single().id

        val (_, otherToken) = client.signupNewUser()
        val response = client.post("/v1/notifications/$notificationId/read") {
            header("Authorization", "Bearer $otherToken")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        val error: SimpleEnvelope = response.body()
        assertEquals("NOTIFICATION_NOT_FOUND", error.error?.code)

        // And the owner's own view must be unaffected (still unread).
        assertFalse(getNotifications(client, ownerToken).data!!.items.single().isRead)
    }

    // -----------------------------------------------------------------
    // Auth / gender
    // -----------------------------------------------------------------

    @Test
    fun `notification endpoints return 401 without a bearer token`() = testApplication {
        val client = jsonClient()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/notifications").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/notifications/unread-count").status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post("/v1/notifications/ntf_whatever/read").status,
        )
    }

    @Test
    fun `GET notifications response never contains a gender field`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        finishCompletedRun(client, token)

        val response = client.get("/v1/notifications") { header("Authorization", "Bearer $token") }
        assertFalse(response.bodyAsText().contains("gender"))
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

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

    private suspend fun finish(client: HttpClient, token: String, runId: String): RunFinishEnvelope {
        val response = client.post("/v1/runs/$runId/finish") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(FinishRunRequest(finishedAt = "2026-08-30T01:00:00Z"))
        }
        return response.body()
    }

    /** Runs a full COMPLETED run against rt_001 for [token] and returns its runId. */
    private suspend fun finishCompletedRun(client: HttpClient, token: String): String {
        val runId = startRun(client, token, routeId)
        uploadAll(client, token, runId, GpsFixtures.load("completed_run"))
        val result = finish(client, token, runId)
        assertEquals(RunStatus.COMPLETED, result.data!!.status, "helper assumes the completed_run fixture yields COMPLETED against rt_001")
        return runId
    }

    private suspend fun getNotifications(client: HttpClient, token: String, page: Int? = null, size: Int? = null): NotificationListEnvelope {
        val query = buildList {
            if (page != null) add("page=$page")
            if (size != null) add("size=$size")
        }.joinToString("&")
        val path = if (query.isEmpty()) "/v1/notifications" else "/v1/notifications?$query"
        val response = client.get(path) { header("Authorization", "Bearer $token") }
        return response.body()
    }

    private suspend fun getUnreadCount(client: HttpClient, token: String): UnreadCountEnvelope {
        val response = client.get("/v1/notifications/unread-count") { header("Authorization", "Bearer $token") }
        return response.body()
    }
}
