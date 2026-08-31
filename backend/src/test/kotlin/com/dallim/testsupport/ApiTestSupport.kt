package com.dallim.testsupport

import com.dallim.common.ApiErrorBody
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Shared helpers for the run-domain API integration tests (com.dallim.integration.*).
 *
 * These tests exercise the REAL Application module (com.dallim.module) against the local
 * docker-compose Postgres/PostGIS + Redis (see scripts/dev-db.sh) -- there is no in-memory
 * fake/mock DB, so every test must be written to tolerate a persistent, shared, ever-growing
 * database (unique emails per run, no assertions on absolute counts, etc).
 */
object ApiTestSupport {

    val testJson = Json { ignoreUnknownKeys = true }

    fun ApplicationTestBuilder.jsonClient(): HttpClient {
        // application.conf's `ktor.application.modules = [com.dallim.ApplicationKt.module]`
        // (the same property EngineMain reads in production) makes the test host auto-load
        // com.dallim.module once environment.config points at it -- do NOT also call
        // `application { module() }` here, or module() (and every plugin it installs) runs
        // twice on the same Application and blows up with DuplicatePluginException.
        environment { config = io.ktor.server.config.ApplicationConfig("application-test.conf") }
        return createClient {
            install(ClientContentNegotiation) { json(testJson) }
        }
    }

    /** A fresh, never-before-used email for /auth/signup -- the DB is real and persistent. */
    fun uniqueEmail(): String = "qa-${UUID.randomUUID().toString().take(12)}@dallim.test"

    @Serializable
    data class SimpleApiResponse(val success: Boolean, val error: ApiErrorBody? = null)

    /** Signs up a brand-new EMAIL-provider user and returns (userId, accessToken). */
    suspend fun HttpClient.signupNewUser(email: String = uniqueEmail(), password: String = "qa-Passw0rd"): Pair<String, String> {
        val response = post("/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(SignupBody(email, password))
        }
        val body: AuthResponseEnvelope = response.body()
        val data = requireNotNull(body.data) { "signup failed: ${body.error}" }
        return data.userId to data.accessToken
    }

    suspend fun HttpClient.authGet(path: String, token: String) = get(path) { header("Authorization", "Bearer $token") }

    @Serializable
    data class SignupBody(val email: String, val password: String)

    @Serializable
    data class AuthTokenData(val accessToken: String, val refreshToken: String, val isNewUser: Boolean, val userId: String, val provider: String)

    @Serializable
    data class AuthResponseEnvelope(val success: Boolean, val data: AuthTokenData? = null, val error: ApiErrorBody? = null)
}
