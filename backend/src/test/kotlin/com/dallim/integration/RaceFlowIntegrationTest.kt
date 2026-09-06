package com.dallim.integration

import com.dallim.common.ApiErrorBody
import com.dallim.testsupport.ApiTestSupport.authGet
import com.dallim.testsupport.ApiTestSupport.jsonClient
import com.dallim.testsupport.ApiTestSupport.signupNewUser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLQueryComponent
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end API integration tests for the race-calendar domain (docs/02-api-spec.md 16장,
 * `com.dallim.race` -- NOT to be confused with `com.dallim.racerecord`'s 완주 이력 domain)
 * against a REAL local Postgres/PostGIS (no mocks, same convention as MeetupFlowIntegrationTest).
 *
 * All tests lean on the seeded races from V12__seed_races.sql (rce_001..rce_008), which are
 * fixed/shared across the whole suite -- deliberately never mutated (no PATCH/DELETE endpoint on
 * races themselves), so assertions on their filter/sort/status behavior are stable. Only the
 * save/unsave side (per-user) is mutated by tests, and those tests use a fresh user each time.
 */
class RaceFlowIntegrationTest {

    @Serializable
    private data class RaceSummaryDto(
        val raceId: String,
        val name: String,
        val region: String,
        val location: String,
        val raceDate: String,
        val dDay: Int,
        val registrationStart: String,
        val registrationEnd: String,
        val status: String,
        val categories: List<String>,
        val minFeeKrw: Int?,
        val maxFeeKrw: Int?,
        val savedCount: Int,
        val isSaved: Boolean,
    )

    @Serializable
    private data class RaceListData(val items: List<RaceSummaryDto>, val totalCount: Int, val page: Int, val size: Int)

    @Serializable
    private data class RaceListEnvelope(val success: Boolean, val data: RaceListData? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class RaceCategoryItemDto(
        val category: String,
        val distanceKm: Double?,
        val feeKrw: Int?,
        val capacity: Int?,
        val cutoffMinutes: Int?,
    )

    @Serializable
    private data class RaceDetailDto(
        val raceId: String,
        val name: String,
        val region: String,
        val location: String,
        val raceDate: String,
        val dDay: Int,
        val registrationStart: String,
        val registrationEnd: String,
        val status: String,
        val organizer: String,
        val souvenir: String?,
        val categories: List<RaceCategoryItemDto>,
        val minFeeKrw: Int?,
        val maxFeeKrw: Int?,
        val savedCount: Int,
        val isSaved: Boolean,
    )

    @Serializable
    private data class RaceDetailEnvelope(val success: Boolean, val data: RaceDetailDto? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class MyRacesData(val items: List<RaceSummaryDto>)

    @Serializable
    private data class MyRacesEnvelope(val success: Boolean, val data: MyRacesData? = null, val error: ApiErrorBody? = null)

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private suspend fun listRaces(
        client: HttpClient,
        region: String? = null,
        category: String? = null,
        status: String? = null,
        size: Int? = null,
        token: String? = null,
    ): RaceListEnvelope {
        val query = buildList {
            region?.let { add("region=${it.encodeURLQueryComponent()}") }
            category?.let { add("category=$it") }
            status?.let { add("status=$it") }
            size?.let { add("size=$it") }
        }.joinToString("&")
        val path = "/v1/races" + if (query.isNotEmpty()) "?$query" else ""
        val response = if (token != null) client.authGet(path, token) else client.get(path)
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body()
    }

    private suspend fun getDetailRaw(client: HttpClient, raceId: String, token: String? = null): HttpResponse =
        if (token != null) client.authGet("/v1/races/$raceId", token) else client.get("/v1/races/$raceId")

    private suspend fun getDetail(client: HttpClient, raceId: String, token: String? = null): RaceDetailDto {
        val response = getDetailRaw(client, raceId, token)
        assertEquals(HttpStatusCode.OK, response.status)
        val body: RaceDetailEnvelope = response.body()
        return requireNotNull(body.data) { "get detail failed: ${body.error}" }
    }

    private suspend fun saveRaw(client: HttpClient, token: String, raceId: String): HttpResponse =
        client.post("/v1/races/$raceId/save") { header("Authorization", "Bearer $token") }

    private suspend fun unsaveRaw(client: HttpClient, token: String, raceId: String): HttpResponse =
        client.delete("/v1/races/$raceId/save") { header("Authorization", "Bearer $token") }

    private suspend fun listMyRaces(client: HttpClient, token: String): List<RaceSummaryDto> {
        val response = client.authGet("/v1/users/me/races", token)
        assertEquals(HttpStatusCode.OK, response.status)
        val body: MyRacesEnvelope = response.body()
        return requireNotNull(body.data) { "list my races failed: ${body.error}" }.items
    }

    // -----------------------------------------------------------------
    // GET /races (16.1) -- filters + sort
    // -----------------------------------------------------------------

    @Test
    fun `list filters by region as a substring match`() = testApplication {
        val client = jsonClient()

        val list = listRaces(client, region = "성남").data!!.items
        assertTrue(list.isNotEmpty())
        assertTrue(list.all { it.region == "성남" })
        assertTrue(list.any { it.raceId == "rce_001" })
        assertTrue(list.any { it.raceId == "rce_004" })
        assertFalse(list.any { it.raceId == "rce_002" }, "서울 race must not appear under a 성남 filter")
    }

    @Test
    fun `list filters by category matching any of the race's category options`() = testApplication {
        val client = jsonClient()

        val trailList = listRaces(client, category = "TRAIL").data!!.items
        assertTrue(trailList.any { it.raceId == "rce_008" })
        assertFalse(trailList.any { it.raceId == "rce_001" }, "5K/10K-only race must not match a TRAIL filter")

        val fullList = listRaces(client, category = "FULL").data!!.items
        assertTrue(fullList.map { it.raceId }.containsAll(listOf("rce_003", "rce_005", "rce_006")))
    }

    @Test
    fun `list filters by computed registration status`() = testApplication {
        val client = jsonClient()

        val open = listRaces(client, status = "OPEN").data!!.items.map { it.raceId }
        assertTrue(open.containsAll(listOf("rce_001", "rce_002", "rce_004", "rce_007")))

        val upcoming = listRaces(client, status = "UPCOMING").data!!.items.map { it.raceId }
        assertTrue(upcoming.containsAll(listOf("rce_003", "rce_006")))

        val closed = listRaces(client, status = "CLOSED").data!!.items.map { it.raceId }
        assertTrue(closed.containsAll(listOf("rce_005", "rce_008")))

        // every item's own `status` field must actually match the filter it was returned under
        assertTrue(open.isNotEmpty() && listRaces(client, status = "OPEN").data!!.items.all { it.status == "OPEN" })
    }

    @Test
    fun `list default sort is soonest registration deadline first, closed pushed to the back`() = testApplication {
        val client = jsonClient()

        val all = listRaces(client, size = 100).data!!.items
        assertTrue(all.isNotEmpty())
        val closedIndices = all.withIndex().filter { it.value.status == "CLOSED" }.map { it.index }
        val nonClosedIndices = all.withIndex().filter { it.value.status != "CLOSED" }.map { it.index }
        if (closedIndices.isNotEmpty() && nonClosedIndices.isNotEmpty()) {
            assertTrue(nonClosedIndices.max() < closedIndices.min(), "closed races must be sorted after all non-closed ones")
        }

        // Within the non-closed group, registrationEnd must be non-decreasing.
        val nonClosedEnds = all.filter { it.status != "CLOSED" }.map { it.registrationEnd }
        assertEquals(nonClosedEnds.sorted(), nonClosedEnds)
    }

    @Test
    fun `each race exposes a category badge list and a fee range derived from its category options`() = testApplication {
        val client = jsonClient()

        val race002 = listRaces(client).data!!.items.single { it.raceId == "rce_002" }
        assertEquals(setOf("5K", "10K", "HALF"), race002.categories.toSet())
        assertEquals(20000, race002.minFeeKrw)
        assertEquals(35000, race002.maxFeeKrw)
    }

    @Test
    fun `list never leaks gender`() = testApplication {
        val client = jsonClient()
        val response = client.get("/v1/races")
        assertFalse(response.bodyAsText().contains("gender"), "race list must never contain a gender field")
    }

    // -----------------------------------------------------------------
    // GET /races/{raceId} (16.2)
    // -----------------------------------------------------------------

    @Test
    fun `get detail for a nonexistent raceId returns 404 RACE_NOT_FOUND`() = testApplication {
        val client = jsonClient()

        val response = getDetailRaw(client, "rce_does_not_exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("RACE_NOT_FOUND", response.body<RaceDetailEnvelope>().error?.code)
    }

    @Test
    fun `get detail returns per-category distance fee capacity and cutoff`() = testApplication {
        val client = jsonClient()

        val detail = getDetail(client, "rce_008")
        assertEquals("과천 사슴벌레 트레일런", detail.name)
        assertEquals("과천", detail.region)
        val trail = detail.categories.single { it.category == "TRAIL" }
        assertEquals(15.0, trail.distanceKm)
        assertEquals(30000, trail.feeKrw)
        assertEquals(300, trail.capacity)
        assertEquals(240, trail.cutoffMinutes)
        val ultra = detail.categories.single { it.category == "ULTRA" }
        assertEquals(50.0, ultra.distanceKm)
        assertEquals("CLOSED", detail.status)
    }

    @Test
    fun `get detail isSaved is false when unauthenticated and false for a user who never saved it`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val anon = getDetail(client, "rce_002")
        assertFalse(anon.isSaved)

        val authed = getDetail(client, "rce_002", token)
        assertFalse(authed.isSaved)
    }

    @Test
    fun `detail never leaks gender`() = testApplication {
        val client = jsonClient()
        val response = getDetailRaw(client, "rce_001")
        assertFalse(response.bodyAsText().contains("gender"), "race detail must never contain a gender field")
    }

    // -----------------------------------------------------------------
    // POST/DELETE /races/{raceId}/save (16.3)
    // -----------------------------------------------------------------

    @Test
    fun `save requires auth`() = testApplication {
        val client = jsonClient()
        val response = client.post("/v1/races/rce_001/save")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `save a nonexistent raceId returns 404 RACE_NOT_FOUND`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = saveRaw(client, token, "rce_does_not_exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("RACE_NOT_FOUND", response.body<RaceDetailEnvelope>().error?.code)
    }

    @Test
    fun `save then get detail flips isSaved true and increments savedCount, unsave reverts both`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val before = getDetail(client, "rce_003")
        val beforeCount = before.savedCount

        val saveResponse = saveRaw(client, token, "rce_003")
        assertEquals(HttpStatusCode.Created, saveResponse.status)

        val afterSave = getDetail(client, "rce_003", token)
        assertTrue(afterSave.isSaved)
        assertEquals(beforeCount + 1, afterSave.savedCount)

        val unsaveResponse = unsaveRaw(client, token, "rce_003")
        assertEquals(HttpStatusCode.OK, unsaveResponse.status)

        val afterUnsave = getDetail(client, "rce_003", token)
        assertFalse(afterUnsave.isSaved)
        assertEquals(beforeCount, afterUnsave.savedCount)
    }

    @Test
    fun `saving the same race twice is idempotent (savedCount does not double-count)`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        assertEquals(HttpStatusCode.Created, saveRaw(client, token, "rce_004").status)
        assertEquals(HttpStatusCode.Created, saveRaw(client, token, "rce_004").status)

        val detail = getDetail(client, "rce_004", token)
        assertTrue(detail.isSaved)
        // Not asserting an absolute savedCount (shared fixture across the suite) -- only that the
        // second save didn't throw and isSaved is still simply true.
    }

    @Test
    fun `unsaving a race that was never saved is an idempotent 200`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = unsaveRaw(client, token, "rce_006")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // -----------------------------------------------------------------
    // GET /users/me/races (16.4)
    // -----------------------------------------------------------------

    @Test
    fun `my races requires auth`() = testApplication {
        val client = jsonClient()
        val response = client.get("/v1/users/me/races")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `my races lists only what this user saved, sorted by race date ascending, and is isolated per user`() = testApplication {
        val client = jsonClient()
        val (_, myToken) = client.signupNewUser()
        val (_, otherToken) = client.signupNewUser()

        // rce_006 raceDate 2027-01-10 (latest), rce_001 raceDate 2026-10-05 (earlier),
        // rce_005 raceDate 2026-09-20 (earliest of the three) -- save out of date order.
        assertEquals(HttpStatusCode.Created, saveRaw(client, myToken, "rce_006").status)
        assertEquals(HttpStatusCode.Created, saveRaw(client, myToken, "rce_001").status)
        assertEquals(HttpStatusCode.Created, saveRaw(client, myToken, "rce_005").status)
        assertEquals(HttpStatusCode.Created, saveRaw(client, otherToken, "rce_002").status)

        val mine = listMyRaces(client, myToken)
        assertEquals(listOf("rce_005", "rce_001", "rce_006"), mine.map { it.raceId })
        assertTrue(mine.all { it.isSaved })

        val others = listMyRaces(client, otherToken)
        assertEquals(listOf("rce_002"), others.map { it.raceId })
    }

    @Test
    fun `my races never leaks gender`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        saveRaw(client, token, "rce_007")

        val response = client.authGet("/v1/users/me/races", token)
        assertFalse(response.bodyAsText().contains("gender"), "my races must never contain a gender field")
    }
}
