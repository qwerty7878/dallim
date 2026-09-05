package com.dallim.integration

import com.dallim.common.ApiErrorBody
import com.dallim.meetup.MeetupTable
import com.dallim.notification.NotificationTable
import com.dallim.notification.NotificationType
import com.dallim.testsupport.ApiTestSupport.authGet
import com.dallim.testsupport.ApiTestSupport.jsonClient
import com.dallim.testsupport.ApiTestSupport.signupNewUser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
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
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end API integration tests for the run-together recruiting domain (docs/02-api-spec.md
 * 14장, docs/01-feature-spec.md 1.8) against a REAL local Postgres/PostGIS (scripts/dev-db.sh) --
 * same no-mocks convention as HomeFlowIntegrationTest/RunFlowIntegrationTest.
 *
 * All tests share the seeded route `rt_004` (see V2__seed_curated_routes.sql) -- deliberately a
 * route nothing else in the suite touches (RunFlowIntegrationTest/HomeFlowIntegrationTest lean on
 * rt_001/rt_002), so this file doesn't have to filter around unrelated runs. Since
 * `GET /routes/{routeId}/meetups` is still a *shared, ever-growing* list across test runs, list
 * assertions filter down to the meetup ids each test itself created rather than asserting on
 * absolute counts/positions.
 */
class MeetupFlowIntegrationTest {

    private val routeId = "rt_004"

    private val directDb: Database by lazy {
        Database.connect(
            url = "jdbc:postgresql://localhost:5432/dallim",
            driver = "org.postgresql.Driver",
            user = "dallim",
            password = "dallim",
        )
    }

    @Serializable
    private data class CreateMeetupData(val meetupId: String)

    @Serializable
    private data class CreateMeetupEnvelope(val success: Boolean, val data: CreateMeetupData? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class MeetupListItemDto(
        val meetupId: String,
        val hostNickname: String,
        val scheduledAt: String,
        val maxParticipants: Int,
        val currentParticipants: Int,
        val status: String,
        val isFull: Boolean,
        val isPast: Boolean,
    )

    @Serializable
    private data class MeetupListData(val items: List<MeetupListItemDto>)

    @Serializable
    private data class MeetupListEnvelope(val success: Boolean, val data: MeetupListData? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class ParticipantDto(val userId: String, val nickname: String, val isHost: Boolean)

    @Serializable
    private data class MeetupDetailData(
        val meetupId: String,
        val routeId: String,
        val routeName: String,
        val hostUserId: String,
        val hostNickname: String,
        val scheduledAt: String,
        val maxParticipants: Int,
        val description: String?,
        val status: String,
        val isFull: Boolean,
        val isPast: Boolean,
        val isHost: Boolean,
        val isJoined: Boolean,
        val participants: List<ParticipantDto>,
    )

    @Serializable
    private data class MeetupDetailEnvelope(val success: Boolean, val data: MeetupDetailData? = null, val error: ApiErrorBody? = null)

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private fun futureIso(secondsFromNow: Long = 3600): String = Instant.now().plusSeconds(secondsFromNow).toString()

    private suspend fun registerProfile(client: HttpClient, token: String, nickname: String) {
        val response = client.post("/v1/users/me/profile") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                """{"nickname":"$nickname","avatarId":"avatar_01","runningExperience":"UNDER_3_MONTHS","comfortablePace":"PACE_6_7","gender":"MALE"}""",
            )
        }
        assertEquals(HttpStatusCode.Created, response.status, "profile registration must succeed for test setup")
    }

    private fun uniqueNickname(): String = "mt-${UUID.randomUUID().toString().take(10)}"

    private suspend fun createMeetupRaw(
        client: HttpClient,
        token: String,
        scheduledAt: String = futureIso(),
        maxParticipants: Int = 6,
        description: String? = "천천히 페이스로 완주 목표예요",
        forRouteId: String = routeId,
    ): HttpResponse = client.post("/v1/routes/$forRouteId/meetups") {
        header("Authorization", "Bearer $token")
        contentType(ContentType.Application.Json)
        val descJson = if (description == null) "null" else "\"$description\""
        setBody("""{"scheduledAt":"$scheduledAt","maxParticipants":$maxParticipants,"description":$descJson}""")
    }

    private suspend fun createMeetup(
        client: HttpClient,
        token: String,
        scheduledAt: String = futureIso(),
        maxParticipants: Int = 6,
    ): String {
        val response = createMeetupRaw(client, token, scheduledAt, maxParticipants)
        assertEquals(HttpStatusCode.Created, response.status)
        val body: CreateMeetupEnvelope = response.body()
        return requireNotNull(body.data) { "create meetup failed: ${body.error}" }.meetupId
    }

    private suspend fun getList(client: HttpClient, forRouteId: String = routeId): MeetupListEnvelope {
        val response = client.get("/v1/routes/$forRouteId/meetups")
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body()
    }

    private suspend fun getDetailRaw(client: HttpClient, token: String, meetupId: String): HttpResponse =
        client.authGet("/v1/meetups/$meetupId", token)

    private suspend fun getDetail(client: HttpClient, token: String, meetupId: String): MeetupDetailData {
        val response = getDetailRaw(client, token, meetupId)
        assertEquals(HttpStatusCode.OK, response.status)
        val body: MeetupDetailEnvelope = response.body()
        return requireNotNull(body.data) { "get detail failed: ${body.error}" }
    }

    private suspend fun joinRaw(client: HttpClient, token: String, meetupId: String): HttpResponse =
        client.post("/v1/meetups/$meetupId/join") { header("Authorization", "Bearer $token") }

    private suspend fun leaveRaw(client: HttpClient, token: String, meetupId: String): HttpResponse =
        client.post("/v1/meetups/$meetupId/leave") { header("Authorization", "Bearer $token") }

    private suspend fun deleteRaw(client: HttpClient, token: String, meetupId: String): HttpResponse =
        client.delete("/v1/meetups/$meetupId") { header("Authorization", "Bearer $token") }

    private fun backdateScheduledAt(meetupId: String, instant: Instant) {
        transaction(directDb) {
            MeetupTable.update({ MeetupTable.id eq meetupId }) {
                it[MeetupTable.scheduledAt] = instant
            }
        }
    }

    private fun countMeetupJoinedNotifications(hostUserId: String): Long = transaction(directDb) {
        NotificationTable.selectAll()
            .where { (NotificationTable.userId eq hostUserId) and (NotificationTable.type eq NotificationType.MEETUP_JOINED) }
            .count()
    }

    // -----------------------------------------------------------------
    // POST /routes/{routeId}/meetups (14.3)
    // -----------------------------------------------------------------

    @Test
    fun `create meetup rejects a past scheduledAt with 400 VALIDATION_ERROR`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = createMeetupRaw(client, token, scheduledAt = Instant.now().minusSeconds(3600).toString())

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body: CreateMeetupEnvelope = response.body()
        assertEquals("VALIDATION_ERROR", body.error?.code)
    }

    @Test
    fun `create meetup rejects maxParticipants outside 2 to 20 with 400 VALIDATION_ERROR`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val tooSmall = createMeetupRaw(client, token, maxParticipants = 1)
        assertEquals(HttpStatusCode.BadRequest, tooSmall.status)
        assertEquals("VALIDATION_ERROR", tooSmall.body<CreateMeetupEnvelope>().error?.code)

        val tooBig = createMeetupRaw(client, token, maxParticipants = 21)
        assertEquals(HttpStatusCode.BadRequest, tooBig.status)
        assertEquals("VALIDATION_ERROR", tooBig.body<CreateMeetupEnvelope>().error?.code)
    }

    @Test
    fun `create meetup for a nonexistent routeId returns 404 ROUTE_NOT_FOUND`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = createMeetupRaw(client, token, forRouteId = "rt_does_not_exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("ROUTE_NOT_FOUND", response.body<CreateMeetupEnvelope>().error?.code)
    }

    @Test
    fun `create meetup auto-registers the host as a participant and never leaks gender`() = testApplication {
        val client = jsonClient()
        val (hostUserId, hostToken) = client.signupNewUser()
        val hostNickname = uniqueNickname()
        registerProfile(client, hostToken, hostNickname)

        val meetupId = createMeetup(client, hostToken)

        val detailResponse = getDetailRaw(client, hostToken, meetupId)
        assertFalse(detailResponse.bodyAsText().contains("gender"), "meetup detail must never contain a gender field")

        val detail = getDetail(client, hostToken, meetupId)
        assertEquals(hostUserId, detail.hostUserId)
        assertEquals(hostNickname, detail.hostNickname)
        assertTrue(detail.isHost)
        assertTrue(detail.isJoined, "host must be auto-registered as a participant")
        assertEquals(1, detail.participants.size)
        assertEquals(hostUserId, detail.participants.single().userId)
        assertTrue(detail.participants.single().isHost)
        assertEquals("OPEN", detail.status)
        assertFalse(detail.isFull)
        assertFalse(detail.isPast)
    }

    // -----------------------------------------------------------------
    // GET /routes/{routeId}/meetups (14.2)
    // -----------------------------------------------------------------

    @Test
    fun `list is sorted by scheduledAt ascending and requires no auth`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val later = createMeetup(client, token, scheduledAt = futureIso(7200))
        val soonest = createMeetup(client, token, scheduledAt = futureIso(1800))
        val middle = createMeetup(client, token, scheduledAt = futureIso(3600))

        val list = getList(client).data!!.items
        val ours = list.filter { it.meetupId in setOf(later, soonest, middle) }

        assertEquals(listOf(soonest, middle, later), ours.map { it.meetupId })
    }

    @Test
    fun `list computes isFull and isPast server-side`() = testApplication {
        val client = jsonClient()
        val (_, hostToken) = client.signupNewUser()
        val (_, otherToken) = client.signupNewUser()

        val fullMeetupId = createMeetup(client, hostToken, maxParticipants = 2)
        assertEquals(HttpStatusCode.OK, joinRaw(client, otherToken, fullMeetupId).status)

        val openMeetupId = createMeetup(client, hostToken, maxParticipants = 6)

        val list = getList(client).data!!.items.associateBy { it.meetupId }
        assertTrue(list.getValue(fullMeetupId).isFull)
        assertEquals(2, list.getValue(fullMeetupId).currentParticipants)
        assertFalse(list.getValue(openMeetupId).isFull)
        assertFalse(list.getValue(fullMeetupId).isPast)
        assertFalse(list.getValue(openMeetupId).isPast)
    }

    @Test
    fun `list for a nonexistent routeId returns 404 ROUTE_NOT_FOUND`() = testApplication {
        val client = jsonClient()
        val response = client.get("/v1/routes/rt_does_not_exist/meetups")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("ROUTE_NOT_FOUND", response.body<MeetupListEnvelope>().error?.code)
    }

    // -----------------------------------------------------------------
    // GET /meetups/{meetupId} (14.4)
    // -----------------------------------------------------------------

    @Test
    fun `get detail for a nonexistent meetupId returns 404 MEETUP_NOT_FOUND`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = getDetailRaw(client, token, "mt_does_not_exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("MEETUP_NOT_FOUND", response.body<MeetupDetailEnvelope>().error?.code)
    }

    @Test
    fun `isHost and isJoined are false for a stranger who never joined`() = testApplication {
        val client = jsonClient()
        val (_, hostToken) = client.signupNewUser()
        val (_, strangerToken) = client.signupNewUser()

        val meetupId = createMeetup(client, hostToken)

        val detail = getDetail(client, strangerToken, meetupId)
        assertFalse(detail.isHost)
        assertFalse(detail.isJoined)
    }

    // -----------------------------------------------------------------
    // POST /meetups/{meetupId}/join (14.4)
    // -----------------------------------------------------------------

    @Test
    fun `join succeeds and notifies the host, and isJoined flips true`() = testApplication {
        val client = jsonClient()
        val (hostUserId, hostToken) = client.signupNewUser()
        val (_, joinerToken) = client.signupNewUser()
        val joinerNickname = uniqueNickname()
        registerProfile(client, joinerToken, joinerNickname)

        val meetupId = createMeetup(client, hostToken)
        val before = countMeetupJoinedNotifications(hostUserId)

        val response = joinRaw(client, joinerToken, meetupId)
        assertEquals(HttpStatusCode.OK, response.status)

        val detail = getDetail(client, joinerToken, meetupId)
        assertTrue(detail.isJoined)
        assertEquals(2, detail.participants.size)
        assertTrue(detail.participants.any { it.nickname == joinerNickname && !it.isHost })

        assertEquals(before + 1, countMeetupJoinedNotifications(hostUserId), "joining must create exactly one MEETUP_JOINED notification for the host")
    }

    @Test
    fun `join by the host again returns 409 ALREADY_JOINED`() = testApplication {
        val client = jsonClient()
        val (_, hostToken) = client.signupNewUser()
        val meetupId = createMeetup(client, hostToken)

        val response = joinRaw(client, hostToken, meetupId)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("ALREADY_JOINED", response.body<CreateMeetupEnvelope>().error?.code)
    }

    @Test
    fun `join twice by the same non-host user returns 409 ALREADY_JOINED on the second call`() = testApplication {
        val client = jsonClient()
        val (_, hostToken) = client.signupNewUser()
        val (_, joinerToken) = client.signupNewUser()
        val meetupId = createMeetup(client, hostToken)

        assertEquals(HttpStatusCode.OK, joinRaw(client, joinerToken, meetupId).status)
        val second = joinRaw(client, joinerToken, meetupId)

        assertEquals(HttpStatusCode.Conflict, second.status)
        assertEquals("ALREADY_JOINED", second.body<CreateMeetupEnvelope>().error?.code)
    }

    @Test
    fun `join a full meetup returns 409 MEETUP_FULL`() = testApplication {
        val client = jsonClient()
        val (_, hostToken) = client.signupNewUser()
        val (_, firstJoinerToken) = client.signupNewUser()
        val (_, secondJoinerToken) = client.signupNewUser()

        // maxParticipants = 2: host + firstJoiner already fills it.
        val meetupId = createMeetup(client, hostToken, maxParticipants = 2)
        assertEquals(HttpStatusCode.OK, joinRaw(client, firstJoinerToken, meetupId).status)

        val response = joinRaw(client, secondJoinerToken, meetupId)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("MEETUP_FULL", response.body<CreateMeetupEnvelope>().error?.code)
    }

    @Test
    fun `join a cancelled meetup returns 409 MEETUP_ENDED`() = testApplication {
        val client = jsonClient()
        val (_, hostToken) = client.signupNewUser()
        val (_, joinerToken) = client.signupNewUser()
        val meetupId = createMeetup(client, hostToken)

        assertEquals(HttpStatusCode.OK, deleteRaw(client, hostToken, meetupId).status)

        val response = joinRaw(client, joinerToken, meetupId)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("MEETUP_ENDED", response.body<CreateMeetupEnvelope>().error?.code)
    }

    @Test
    fun `join a meetup whose scheduledAt has passed returns 409 MEETUP_ENDED`() = testApplication {
        val client = jsonClient()
        val (_, hostToken) = client.signupNewUser()
        val (_, joinerToken) = client.signupNewUser()
        val meetupId = createMeetup(client, hostToken)
        backdateScheduledAt(meetupId, Instant.now().minusSeconds(60))

        val response = joinRaw(client, joinerToken, meetupId)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("MEETUP_ENDED", response.body<CreateMeetupEnvelope>().error?.code)

        // Also surfaces as isPast in both list and detail (14.1/14.2).
        val list = getList(client).data!!.items.associateBy { it.meetupId }
        assertTrue(list.getValue(meetupId).isPast)
    }

    @Test
    fun `join a nonexistent meetupId returns 404 MEETUP_NOT_FOUND`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = joinRaw(client, token, "mt_does_not_exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("MEETUP_NOT_FOUND", response.body<CreateMeetupEnvelope>().error?.code)
    }

    // -----------------------------------------------------------------
    // POST /meetups/{meetupId}/leave (14.4)
    // -----------------------------------------------------------------

    @Test
    fun `leave by the host returns 400 VALIDATION_ERROR`() = testApplication {
        val client = jsonClient()
        val (_, hostToken) = client.signupNewUser()
        val meetupId = createMeetup(client, hostToken)

        val response = leaveRaw(client, hostToken, meetupId)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("VALIDATION_ERROR", response.body<CreateMeetupEnvelope>().error?.code)

        // Host must still be a participant afterward -- leave must not have side-effected anything.
        val detail = getDetail(client, hostToken, meetupId)
        assertTrue(detail.isJoined)
    }

    @Test
    fun `leave by a user who never joined is an idempotent 200`() = testApplication {
        val client = jsonClient()
        val (_, hostToken) = client.signupNewUser()
        val (_, strangerToken) = client.signupNewUser()
        val meetupId = createMeetup(client, hostToken)

        val response = leaveRaw(client, strangerToken, meetupId)

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `a joined participant can leave, freeing up a slot`() = testApplication {
        val client = jsonClient()
        val (_, hostToken) = client.signupNewUser()
        val (_, joinerToken) = client.signupNewUser()
        val meetupId = createMeetup(client, hostToken, maxParticipants = 2)
        assertEquals(HttpStatusCode.OK, joinRaw(client, joinerToken, meetupId).status)

        val leaveResponse = leaveRaw(client, joinerToken, meetupId)
        assertEquals(HttpStatusCode.OK, leaveResponse.status)

        val detail = getDetail(client, joinerToken, meetupId)
        assertFalse(detail.isJoined)
        assertEquals(1, detail.participants.size)
        assertFalse(detail.isFull, "leaving must free up the slot that made it full")

        // Idempotent -- leaving again after already having left is still 200, not an error.
        assertEquals(HttpStatusCode.OK, leaveRaw(client, joinerToken, meetupId).status)
    }

    @Test
    fun `leave a nonexistent meetupId returns 404 MEETUP_NOT_FOUND`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = leaveRaw(client, token, "mt_does_not_exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("MEETUP_NOT_FOUND", response.body<CreateMeetupEnvelope>().error?.code)
    }

    // -----------------------------------------------------------------
    // DELETE /meetups/{meetupId} (14.4)
    // -----------------------------------------------------------------

    @Test
    fun `delete by a non-host returns 403 MEETUP_NOT_HOST`() = testApplication {
        val client = jsonClient()
        val (_, hostToken) = client.signupNewUser()
        val (_, strangerToken) = client.signupNewUser()
        val meetupId = createMeetup(client, hostToken)

        val response = deleteRaw(client, strangerToken, meetupId)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("MEETUP_NOT_HOST", response.body<CreateMeetupEnvelope>().error?.code)

        // Must remain OPEN -- an unauthorized delete attempt has no effect.
        val list = getList(client).data!!.items.associateBy { it.meetupId }
        assertEquals("OPEN", list.getValue(meetupId).status)
    }

    @Test
    fun `delete by the host soft-cancels instead of removing the row`() = testApplication {
        val client = jsonClient()
        val (_, hostToken) = client.signupNewUser()
        val meetupId = createMeetup(client, hostToken)

        val response = deleteRaw(client, hostToken, meetupId)
        assertEquals(HttpStatusCode.OK, response.status)

        // Still visible in the list (as CANCELLED), not silently gone (1.8.2 / 14.4).
        val list = getList(client).data!!.items.associateBy { it.meetupId }
        assertEquals("CANCELLED", list.getValue(meetupId).status)

        val detail = getDetail(client, hostToken, meetupId)
        assertEquals("CANCELLED", detail.status)

        // Idempotent -- deleting an already-cancelled meetup is still a clean 200.
        assertEquals(HttpStatusCode.OK, deleteRaw(client, hostToken, meetupId).status)
    }

    @Test
    fun `delete a nonexistent meetupId returns 404 MEETUP_NOT_FOUND`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = deleteRaw(client, token, "mt_does_not_exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("MEETUP_NOT_FOUND", response.body<CreateMeetupEnvelope>().error?.code)
    }
}
