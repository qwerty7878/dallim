package com.dallim.notification

import com.dallim.push.DeviceTokenRepository
import com.dallim.push.FcmPushService
import com.dallim.push.FcmSendResult
import com.dallim.push.FcmSender
import org.jetbrains.exposed.sql.Database
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure unit tests for [NotificationService] -- no real Postgres/Firebase, both dependencies are
 * faked by subclassing and overriding every method (NotificationRepository.create /
 * FcmPushService.sendToUser are `open` for exactly this), same style as
 * com.dallim.discovery.DiscoveryServiceTest's FakeOsrmClient. Covers docs/02-api-spec.md 10.2's
 * two requirements on the NotificationService side: the push fires with the SAME title/body as
 * the in-app record, and a push failure never affects that record's creation.
 */
class NotificationServiceTest {

    /** A [Database] handle that is never actually queried -- both fakes below override every
     * method that would touch it, so this only needs to satisfy the constructor. */
    private fun unusedDatabase(): Database =
        Database.connect(url = "jdbc:postgresql://unused:5432/unused", driver = "org.postgresql.Driver")

    private class FakeNotificationRepository(database: Database) : NotificationRepository(database) {
        data class CreateCall(
            val userId: String,
            val type: NotificationType,
            val title: String,
            val body: String,
            val relatedRunId: String?,
        )

        val created = mutableListOf<CreateCall>()

        override fun create(userId: String, type: NotificationType, title: String, body: String, relatedRunId: String?): String {
            created += CreateCall(userId, type, title, body, relatedRunId)
            return "ntf_fake"
        }
    }

    private class NeverCalledFcmSender : FcmSender {
        override fun send(token: String, title: String, body: String): FcmSendResult =
            error("FcmSender should never be reached directly -- FcmPushService.sendToUser is faked")
    }

    private class FakeFcmPushService(deviceTokenRepository: DeviceTokenRepository, fcmSender: FcmSender) :
        FcmPushService(deviceTokenRepository, fcmSender) {
        data class SendCall(val userId: String, val title: String, val body: String)

        val sends = mutableListOf<SendCall>()
        var shouldThrow = false

        override fun sendToUser(userId: String, title: String, body: String) {
            sends += SendCall(userId, title, body)
            if (shouldThrow) error("simulated push failure")
        }
    }

    private fun newService(): Triple<NotificationService, FakeNotificationRepository, FakeFcmPushService> {
        val db = unusedDatabase()
        val repository = FakeNotificationRepository(db)
        val push = FakeFcmPushService(DeviceTokenRepository(db), NeverCalledFcmSender())
        return Triple(NotificationService(repository, push), repository, push)
    }

    @Test
    fun `notifyRunCompleted creates the in-app record and pushes the identical title body to the user's devices`() {
        val (service, repository, push) = newService()

        service.notifyRunCompleted(userId = "usr_1", runId = "run_1", routeName = "고래", distanceKm = 5.1)

        val created = repository.created.single()
        assertEquals("usr_1", created.userId)
        assertEquals(NotificationType.RUN_COMPLETED, created.type)
        assertEquals("run_1", created.relatedRunId)
        assertTrue(created.body.contains("고래"))

        val sent = push.sends.single()
        assertEquals("usr_1", sent.userId)
        assertEquals(created.title, sent.title, "push title must match the in-app notification's title exactly (10.2)")
        assertEquals(created.body, sent.body, "push body must match the in-app notification's body exactly (10.2)")
    }

    @Test
    fun `a push failure is swallowed and never prevents or fails the in-app record creation`() {
        val (service, repository, push) = newService()
        push.shouldThrow = true

        // Must not throw out of notifyRunCompleted -- see NotificationService.pushToDevices.
        service.notifyRunCompleted(userId = "usr_2", runId = "run_2", routeName = "별", distanceKm = 3.0)

        assertEquals(1, repository.created.size, "in-app record must still be created despite the push failure")
        assertEquals(1, push.sends.size, "push must still have been attempted")
    }
}
