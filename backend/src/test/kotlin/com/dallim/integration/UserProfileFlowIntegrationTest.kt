package com.dallim.integration

import com.dallim.common.ApiErrorBody
import com.dallim.testsupport.ApiTestSupport.authGet
import com.dallim.testsupport.ApiTestSupport.jsonClient
import com.dallim.testsupport.ApiTestSupport.signupNewUser
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
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end API integration tests for the user/profile domain (docs/02-api-spec.md 2장) against
 * a REAL local Postgres/PostGIS (see scripts/dev-db.sh), following the same no-mocks style as
 * RunFlowIntegrationTest:
 *
 *   GET /users/nickname-check -> POST /users/me/profile -> GET /users/me
 */
class UserProfileFlowIntegrationTest {

    @Serializable
    private data class NicknameCheckData(val available: Boolean)

    @Serializable
    private data class NicknameCheckEnvelope(val success: Boolean, val data: NicknameCheckData? = null)

    @Serializable
    private data class ProfileData(val userId: String, val nickname: String)

    @Serializable
    private data class ProfileEnvelope(val success: Boolean, val data: ProfileData? = null, val error: ApiErrorBody? = null)

    @Serializable
    private data class MeData(
        val userId: String,
        val nickname: String,
        val avatarId: String,
        val runningExperience: String,
        val comfortablePace: String,
        val totalRuns: Int,
        val totalDistanceKm: Double,
    )

    @Serializable
    private data class MeEnvelope(val success: Boolean, val data: MeData? = null, val error: ApiErrorBody? = null)

    private fun uniqueNickname(): String = "nick-${UUID.randomUUID().toString().take(10)}"

    @Test
    fun `full flow - nickname-check, register profile, fetch me, never leaking gender`() = testApplication {
        val client = jsonClient()
        val (userId, token) = client.signupNewUser()
        val nickname = uniqueNickname()

        // GET /users/nickname-check works with no auth at all.
        val checkResponse = client.get("/v1/users/nickname-check?value=$nickname")
        assertEquals(HttpStatusCode.OK, checkResponse.status)
        val checkBody: NicknameCheckEnvelope = checkResponse.body()
        assertTrue(checkBody.data!!.available, "a freshly-generated random nickname must be available")

        // POST /users/me/profile
        val profileRequestJson = """
            {
              "nickname": "$nickname",
              "avatarId": "avatar_03",
              "runningExperience": "UNDER_3_MONTHS",
              "comfortablePace": "PACE_6_7",
              "gender": "FEMALE"
            }
        """.trimIndent()
        val registerResponse = client.post("/v1/users/me/profile") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(profileRequestJson)
        }
        assertEquals(HttpStatusCode.Created, registerResponse.status)
        assertFalse(registerResponse.bodyAsText().contains("gender"), "POST /users/me/profile response must never contain a gender field")
        val registerBody: ProfileEnvelope = registerResponse.body()
        assertEquals(userId, registerBody.data!!.userId)
        assertEquals(nickname, registerBody.data.nickname)

        // The nickname is now taken.
        val recheckResponse = client.get("/v1/users/nickname-check?value=$nickname")
        val recheckBody: NicknameCheckEnvelope = recheckResponse.body()
        assertFalse(recheckBody.data!!.available, "nickname must no longer be available after registration")

        // GET /users/me reflects the registered profile and never leaks gender.
        val meResponse = client.authGet("/v1/users/me", token)
        assertEquals(HttpStatusCode.OK, meResponse.status)
        assertFalse(meResponse.bodyAsText().contains("gender"), "GET /users/me response must never contain a gender field")
        val meBody: MeEnvelope = meResponse.body()
        assertEquals(userId, meBody.data!!.userId)
        assertEquals(nickname, meBody.data.nickname)
        assertEquals("avatar_03", meBody.data.avatarId)
        assertEquals("UNDER_3_MONTHS", meBody.data.runningExperience)
        assertEquals("PACE_6_7", meBody.data.comfortablePace)
        assertEquals(0, meBody.data.totalRuns)
    }

    @Test
    fun `POST users me profile with an already-taken nickname returns 409 NICKNAME_TAKEN`() = testApplication {
        val client = jsonClient()
        val nickname = uniqueNickname()

        val (_, firstToken) = client.signupNewUser()
        val firstRequest = """
            {"nickname":"$nickname","avatarId":"avatar_01","runningExperience":"UNDER_3_MONTHS","comfortablePace":"PACE_6_7","gender":"MALE"}
        """.trimIndent()
        val firstResponse = client.post("/v1/users/me/profile") {
            header("Authorization", "Bearer $firstToken")
            contentType(ContentType.Application.Json)
            setBody(firstRequest)
        }
        assertEquals(HttpStatusCode.Created, firstResponse.status)

        val (_, secondToken) = client.signupNewUser()
        val secondResponse = client.post("/v1/users/me/profile") {
            header("Authorization", "Bearer $secondToken")
            contentType(ContentType.Application.Json)
            setBody(firstRequest)
        }
        assertEquals(HttpStatusCode.Conflict, secondResponse.status)
        val error: ProfileEnvelope = secondResponse.body()
        assertEquals("NICKNAME_TAKEN", error.error?.code)
    }

    @Test
    fun `GET users me and POST users me profile return 401 without a bearer token`() = testApplication {
        val client = jsonClient()

        val meResponse = client.get("/v1/users/me")
        assertEquals(HttpStatusCode.Unauthorized, meResponse.status)

        val profileResponse = client.post("/v1/users/me/profile") {
            contentType(ContentType.Application.Json)
            setBody("""{"nickname":"x","avatarId":"avatar_01","runningExperience":"UNDER_3_MONTHS","comfortablePace":"PACE_6_7","gender":"MALE"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, profileResponse.status)
    }
}
