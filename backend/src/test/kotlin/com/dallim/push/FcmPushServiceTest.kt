package com.dallim.push

import com.dallim.user.AuthProvider
import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises [FcmPushService] + the REAL [DeviceTokenRepository] against a real local Postgres
 * (see scripts/dev-db.sh) with a fake [FcmSender] standing in for the network/Firebase Admin SDK
 * -- same "real DB, fake the external client" style as com.dallim.integration.*
 * IntegrationTest classes, just without needing the full Ktor test host since this domain has no
 * dependency on it. Covers docs/02-api-spec.md 10.2's two must-haves: every registered token for
 * a user gets a send attempt, and a token FCM reports as UNREGISTERED is quietly deleted while
 * everything else survives.
 */
class FcmPushServiceTest {

    private val testDb: Database by lazy {
        Database.connect(
            url = "jdbc:postgresql://localhost:5432/dallim",
            driver = "org.postgresql.Driver",
            user = "dallim",
            password = "dallim",
        )
    }

    private fun insertTestUser(): String {
        val userId = "usr_fcmtest_${UUID.randomUUID().toString().take(10)}"
        transaction(testDb) {
            UserTable.insert {
                it[id] = userId
                it[provider] = AuthProvider.EMAIL
                it[providerId] = "$userId@dallim.test"
            }
        }
        return userId
    }

    private fun uniqueToken() = "fcm-token-${UUID.randomUUID()}"

    private fun tokenExists(fcmToken: String): Boolean = transaction(testDb) {
        DeviceTokenTable.selectAll().where { DeviceTokenTable.fcmToken eq fcmToken }.count() > 0
    }

    private class FakeFcmSender(private val results: Map<String, FcmSendResult>) : FcmSender {
        val calls = mutableListOf<String>()

        override fun send(token: String, title: String, body: String): FcmSendResult {
            calls += token
            return results[token] ?: FcmSendResult.Success
        }
    }

    @Test
    fun `sendToUser attempts every registered token and deletes only the one FCM reports UNREGISTERED`() {
        val userId = insertTestUser()
        val repository = DeviceTokenRepository(testDb)
        val validToken = uniqueToken()
        val invalidToken = uniqueToken()
        repository.upsert(userId, validToken, DevicePlatform.ANDROID)
        repository.upsert(userId, invalidToken, DevicePlatform.ANDROID)

        val fakeSender = FakeFcmSender(mapOf(invalidToken to FcmSendResult.TokenInvalid, validToken to FcmSendResult.Success))
        val service = FcmPushService(repository, fakeSender)

        service.sendToUser(userId, "title", "body")

        assertEquals(setOf(validToken, invalidToken), fakeSender.calls.toSet())
        assertTrue(tokenExists(validToken), "a successfully-sent token must survive")
        assertFalse(tokenExists(invalidToken), "an UNREGISTERED token must be silently deleted (10.2)")
    }

    @Test
    fun `sendToUser for a user with no registered tokens does nothing and does not throw`() {
        val userId = insertTestUser()
        val repository = DeviceTokenRepository(testDb)
        val fakeSender = FakeFcmSender(emptyMap())
        val service = FcmPushService(repository, fakeSender)

        service.sendToUser(userId, "title", "body")

        assertEquals(0, fakeSender.calls.size)
    }

    @Test
    fun `a send() that throws is treated like any other failure -- the token is kept, not deleted`() {
        val userId = insertTestUser()
        val repository = DeviceTokenRepository(testDb)
        val flakyToken = uniqueToken()
        repository.upsert(userId, flakyToken, DevicePlatform.ANDROID)

        val throwingSender = object : FcmSender {
            override fun send(token: String, title: String, body: String): FcmSendResult = error("network blip")
        }
        val service = FcmPushService(repository, throwingSender)

        service.sendToUser(userId, "title", "body") // must not propagate
        assertTrue(tokenExists(flakyToken))
    }
}
