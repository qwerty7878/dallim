package com.dallim.integration

import com.dallim.push.DeviceTokenTable
import com.dallim.testsupport.ApiTestSupport.jsonClient
import com.dallim.testsupport.ApiTestSupport.signupNewUser
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * POST /users/me/device-tokens (docs/02-api-spec.md 10.1) against the REAL local
 * Postgres/PostGIS + Redis (scripts/dev-db.sh) -- same conventions as
 * NotificationFlowIntegrationTest (no mocks, unique ids per test). FCM send behavior itself
 * (10.2) is covered separately by com.dallim.push.FcmPushServiceTest and
 * com.dallim.notification.NotificationServiceTest, neither of which need HTTP.
 */
class DeviceTokenFlowIntegrationTest {

    // Direct DB check, independent of the app's own Koin-managed Database bean -- there is no
    // GET /users/me/device-tokens endpoint to assert row counts through the API (10.3 keeps this
    // domain write-only from the client's perspective).
    private val directDb: Database by lazy {
        Database.connect(
            url = "jdbc:postgresql://localhost:5432/dallim",
            driver = "org.postgresql.Driver",
            user = "dallim",
            password = "dallim",
        )
    }

    private fun countRowsForToken(fcmToken: String): Long = transaction(directDb) {
        DeviceTokenTable.selectAll().where { DeviceTokenTable.fcmToken eq fcmToken }.count()
    }

    @Test
    fun `POST device-tokens is idempotent -- registering the same token twice never creates a duplicate row`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()
        val fcmToken = "fcm-${UUID.randomUUID()}"

        val first = client.post("/v1/users/me/device-tokens") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"fcmToken":"$fcmToken","platform":"ANDROID"}""")
        }
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(1, countRowsForToken(fcmToken))

        // Re-register the exact same token -- must update the existing row in place, never
        // insert a second one.
        val second = client.post("/v1/users/me/device-tokens") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"fcmToken":"$fcmToken","platform":"ANDROID"}""")
        }
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals(1, countRowsForToken(fcmToken))
    }

    @Test
    fun `POST device-tokens rejects a blank fcmToken with 400`() = testApplication {
        val client = jsonClient()
        val (_, token) = client.signupNewUser()

        val response = client.post("/v1/users/me/device-tokens") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"fcmToken":"","platform":"ANDROID"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST device-tokens requires auth`() = testApplication {
        val client = jsonClient()

        val response = client.post("/v1/users/me/device-tokens") {
            contentType(ContentType.Application.Json)
            setBody("""{"fcmToken":"whatever","platform":"ANDROID"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
