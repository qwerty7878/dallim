package com.dallim.route

import com.dallim.common.ApiException
import com.dallim.common.ErrorCodes
import com.dallim.common.HttpClientFactory
import com.dallim.common.KakaoLocalClient
import com.dallim.common.KakaoPlace
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for [PlaceSearchService]'s request validation and response shaping
 * (docs/02-api-spec.md 12.1, GET /routes/places/search) against a fake [KakaoLocalClient] — no
 * real network call. KakaoLocalClient's own soft-fail-to-empty-list behavior is covered separately
 * in com.dallim.common.KakaoLocalClientTest; here we only need to confirm the service passes that
 * empty list straight through as `items: []` rather than treating it as an error.
 */
class PlaceSearchServiceTest {

    /** A programmable fake — never touches the network (fully overridden, no super call). */
    private class FakeKakaoLocalClient(
        private val results: List<KakaoPlace> = emptyList(),
    ) : KakaoLocalClient(HttpClientFactory.create(), "unused") {
        var callCount = 0
        var lastQuery: String? = null
        var lastLat: Double? = null
        var lastLng: Double? = null

        override suspend fun searchKeyword(query: String, lat: Double?, lng: Double?): List<KakaoPlace> {
            callCount++
            lastQuery = query
            lastLat = lat
            lastLng = lng
            return results
        }
    }

    @Test
    fun `search - blank query is a 400 VALIDATION_ERROR without calling Kakao`() {
        val fake = FakeKakaoLocalClient()
        val service = PlaceSearchService(fake)

        val ex = assertFailsWith<ApiException> {
            runBlocking { service.search(query = "   ", lat = null, lng = null) }
        }
        assertEquals(ErrorCodes.VALIDATION_ERROR, ex.code)
        assertEquals(HttpStatusCode.BadRequest, ex.status)
        assertEquals(0, fake.callCount)
    }

    @Test
    fun `search - null query is a 400 VALIDATION_ERROR without calling Kakao`() {
        val fake = FakeKakaoLocalClient()
        val service = PlaceSearchService(fake)

        val ex = assertFailsWith<ApiException> {
            runBlocking { service.search(query = null, lat = null, lng = null) }
        }
        assertEquals(ErrorCodes.VALIDATION_ERROR, ex.code)
        assertEquals(HttpStatusCode.BadRequest, ex.status)
        assertEquals(0, fake.callCount)
    }

    @Test
    fun `search - query is trimmed before being forwarded to Kakao`() {
        val fake = FakeKakaoLocalClient()
        val service = PlaceSearchService(fake)

        runBlocking { service.search(query = "  안양역  ", lat = null, lng = null) }

        assertEquals("안양역", fake.lastQuery)
    }

    @Test
    fun `search - lat-lng are forwarded through to the Kakao client unchanged`() {
        val fake = FakeKakaoLocalClient()
        val service = PlaceSearchService(fake)

        runBlocking { service.search(query = "안양역", lat = 37.3905, lng = 126.9235) }

        assertEquals(37.3905, fake.lastLat)
        assertEquals(126.9235, fake.lastLng)
    }

    @Test
    fun `search - maps KakaoPlace results into PlaceSearchItem verbatim`() {
        val places = listOf(
            KakaoPlace(name = "안양역", address = "경기 안양시 만안구 안양동", lat = 37.4004, lng = 126.9227),
            KakaoPlace(name = "안양중앙공원", address = "경기 안양시 만안구", lat = 37.3950, lng = 126.9200),
        )
        val fake = FakeKakaoLocalClient(results = places)
        val service = PlaceSearchService(fake)

        val response = runBlocking { service.search(query = "안양", lat = null, lng = null) }

        assertEquals(2, response.items.size)
        assertEquals(PlaceSearchItem(name = "안양역", address = "경기 안양시 만안구 안양동", lat = 37.4004, lng = 126.9227), response.items[0])
        assertEquals(
            PlaceSearchItem(name = "안양중앙공원", address = "경기 안양시 만안구", lat = 37.3950, lng = 126.9200),
            response.items[1],
        )
    }

    @Test
    fun `search - Kakao returning no results (soft-fail or genuinely no match) surfaces as items - empty, not an error`() {
        val fake = FakeKakaoLocalClient(results = emptyList())
        val service = PlaceSearchService(fake)

        val response = runBlocking { service.search(query = "존재하지않는장소이름", lat = null, lng = null) }

        assertTrue(response.items.isEmpty())
    }
}
