package com.dallim.common

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Shared Ktor HTTP client for server-to-server calls (Google tokeninfo, Kakao user info —
 * see auth/GoogleAuthClient.kt, auth/KakaoAuthClient.kt; OSRM map-matching — see
 * common/OsrmClient.kt). Registered once in Koin's core module (plugins/Koin.kt) and reused
 * across domains rather than each client creating its own.
 *
 * `requestTimeoutMillis` is set well above CIO's ~15s built-in default: OSRM `/match` on a
 * finger-drawn trace through a dense downtown road network (e.g. central Seoul near Gwanghwamun)
 * can legitimately take 15-20s+ for the map-matching search, and the default timeout was killing
 * those requests client-side, turning a slow-but-successful OSRM response into a 500 for the user
 * (see draw-convert 500 reports). Google/Kakao token verification calls finish in well under a
 * second regardless, so raising the ceiling here doesn't change their effective behavior.
 */
object HttpClientFactory {
    private const val REQUEST_TIMEOUT_MILLIS = 45_000L
    private const val CONNECT_TIMEOUT_MILLIS = 10_000L

    fun create(): HttpClient =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            }
        }
}
