package com.dallim.common

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression test for the draw-convert "0.00km" bug: OSRM `/match` splits a loose trace into
 * multiple `matchings` when it can't stitch it into one confident path (default `gaps=split`
 * behavior), and taking only `matchings.first()` silently returns just the first (often tiny)
 * fragment's distance as if it were the whole result. [OsrmClient.match] must request
 * `gaps=ignore` + per-point `radiuses` to avoid the split in the first place, and — belt and
 * braces — must merge every `matchings` entry it does get back rather than taking the first.
 */
class OsrmClientTest {

    private fun clientWith(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }

    @Test
    fun `match sends radiuses and gaps=ignore`() {
        var capturedUrl: Url? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url
            respond(
                content = """{"code":"Ok","matchings":[{"distance":100.0,"geometry":{"coordinates":[[126.9,37.4],[126.91,37.41]]}}]}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = OsrmClient(clientWith(engine), "http://osrm")

        runBlocking {
            client.match(listOf(LatLng(lat = 37.40, lng = 126.90), LatLng(lat = 37.41, lng = 126.91)))
        }

        val params = capturedUrl!!.parameters
        assertEquals("30;30", params["radiuses"])
        assertEquals("ignore", params["gaps"])
    }

    @Test
    fun `match merges multiple matchings instead of taking only the first fragment`() {
        val engine = MockEngine { request ->
            respond(
                content = """
                {
                  "code": "Ok",
                  "matchings": [
                    {"distance": 40.0, "geometry": {"coordinates": [[126.90, 37.40], [126.901, 37.401]]}},
                    {"distance": 260.0, "geometry": {"coordinates": [[126.905, 37.405], [126.91, 37.41]]}}
                  ]
                }
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = OsrmClient(clientWith(engine), "http://osrm")

        val result = runBlocking {
            client.match(
                listOf(
                    LatLng(lat = 37.40, lng = 126.90),
                    LatLng(lat = 37.401, lng = 126.901),
                    LatLng(lat = 37.405, lng = 126.905),
                    LatLng(lat = 37.41, lng = 126.91),
                ),
            )
        }

        // Bug would have returned just the first fragment (distance 40.0, 2 coordinate points).
        assertEquals(300.0, result!!.distanceMeters, 1e-9)
        assertEquals(4, result.geometry.coordinates.size)
        assertEquals(listOf(126.90, 37.40), result.geometry.coordinates[0])
        assertEquals(listOf(126.91, 37.41), result.geometry.coordinates[3])
    }

    @Test
    fun `match returns null when OSRM reports NoMatch`() {
        val engine = MockEngine { request ->
            respond(
                content = """{"code":"NoMatch"}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = OsrmClient(clientWith(engine), "http://osrm")

        val result = runBlocking {
            client.match(listOf(LatLng(lat = 37.40, lng = 126.90), LatLng(lat = 37.401, lng = 126.901)))
        }

        assertNull(result)
    }

    @Test
    fun `match returns null when matchings is empty even with code Ok`() {
        val engine = MockEngine { request ->
            respond(
                content = """{"code":"Ok","matchings":[]}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = OsrmClient(clientWith(engine), "http://osrm")

        val result = runBlocking {
            client.match(listOf(LatLng(lat = 37.40, lng = 126.90), LatLng(lat = 37.401, lng = 126.901)))
        }

        assertNull(result)
    }
}
