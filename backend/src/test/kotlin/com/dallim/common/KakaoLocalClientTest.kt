package com.dallim.common

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [KakaoLocalClient] against a fake Kakao Local response — docs/02-api-spec.md 12.2.
 * Covers the request shape (Authorization header, size=5, x/y bias params), the
 * place_name/address_name/road_address_name -> name/address mapping, and the soft-fail-to-empty
 * policy (12.1) for a missing key, an upstream error status, and a network-level failure.
 */
class KakaoLocalClientTest {

    private fun clientWith(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }

    @Test
    fun `searchKeyword sends the Authorization header and size=5, and omits x-y when no location hint given`() {
        var capturedUrl: Url? = null
        var capturedAuthHeader: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url
            capturedAuthHeader = request.headers["Authorization"]
            respond(
                content = """{"documents":[]}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = KakaoLocalClient(clientWith(engine), "test-api-key")

        runBlocking { client.searchKeyword("안양역", lat = null, lng = null) }

        assertEquals("KakaoAK test-api-key", capturedAuthHeader)
        val params = capturedUrl!!.parameters
        assertEquals("안양역", params["query"])
        assertEquals("5", params["size"])
        assertEquals(null, params["x"])
        assertEquals(null, params["y"])
    }

    @Test
    fun `searchKeyword forwards lat-lng as y-x location bias when both given`() {
        var capturedUrl: Url? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url
            respond(
                content = """{"documents":[]}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = KakaoLocalClient(clientWith(engine), "test-api-key")

        runBlocking { client.searchKeyword("안양역", lat = 37.3905, lng = 126.9235) }

        val params = capturedUrl!!.parameters
        assertEquals("126.9235", params["x"])
        assertEquals("37.3905", params["y"])
    }

    @Test
    fun `searchKeyword maps place_name-address_name-y-x, preferring road_address_name when present`() {
        val engine = MockEngine {
            respond(
                content = """
                {
                  "documents": [
                    {
                      "place_name": "안양역",
                      "address_name": "경기 안양시 만안구 안양동 622",
                      "road_address_name": "경기 안양시 만안구 문예로 1",
                      "x": "126.9227",
                      "y": "37.4004"
                    },
                    {
                      "place_name": "안양역 2번 출구",
                      "address_name": "경기 안양시 만안구 안양동 623",
                      "x": "126.9228",
                      "y": "37.4005"
                    }
                  ]
                }
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = KakaoLocalClient(clientWith(engine), "test-api-key")

        val result = runBlocking { client.searchKeyword("안양역", lat = null, lng = null) }

        assertEquals(2, result.size)
        assertEquals("안양역", result[0].name)
        assertEquals("경기 안양시 만안구 문예로 1", result[0].address) // road_address_name preferred
        assertEquals(37.4004, result[0].lat, 1e-9)
        assertEquals(126.9227, result[0].lng, 1e-9)

        assertEquals("안양역 2번 출구", result[1].name)
        assertEquals("경기 안양시 만안구 안양동 623", result[1].address) // falls back to address_name
    }

    @Test
    fun `searchKeyword returns an empty list without any network call when the api key is blank`() {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            respond(content = """{"documents":[]}""", headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val client = KakaoLocalClient(clientWith(engine), "")

        val result = runBlocking { client.searchKeyword("안양역", lat = null, lng = null) }

        assertTrue(result.isEmpty())
        assertEquals(0, callCount)
    }

    @Test
    fun `searchKeyword returns an empty list without any network call when the api key is null`() {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            respond(content = """{"documents":[]}""", headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val client = KakaoLocalClient(clientWith(engine), null)

        val result = runBlocking { client.searchKeyword("안양역", lat = null, lng = null) }

        assertTrue(result.isEmpty())
        assertEquals(0, callCount)
    }

    @Test
    fun `searchKeyword returns an empty list (not a throw) when Kakao responds with a non-2xx status`() {
        val engine = MockEngine {
            respondError(HttpStatusCode.Unauthorized, content = """{"errorType":"WrongAPIKey"}""")
        }
        val client = KakaoLocalClient(clientWith(engine), "bad-key")

        val result = runBlocking { client.searchKeyword("안양역", lat = null, lng = null) }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchKeyword returns an empty list (not a throw) when the call itself fails`() {
        val engine = MockEngine {
            throw java.io.IOException("connection reset")
        }
        val client = KakaoLocalClient(clientWith(engine), "test-api-key")

        val result = runBlocking { client.searchKeyword("안양역", lat = null, lng = null) }

        assertTrue(result.isEmpty())
    }
}
