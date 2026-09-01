package com.dallim.common

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Shared Ktor HTTP client for server-to-server calls (Google tokeninfo, Kakao user info —
 * see auth/GoogleAuthClient.kt, auth/KakaoAuthClient.kt). Registered once in Koin's core
 * module (plugins/Koin.kt) and reused across domains rather than each client creating its own.
 */
object HttpClientFactory {
    fun create(): HttpClient =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
}
