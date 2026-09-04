package com.dallim.push

import com.dallim.common.IdGenerator
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/**
 * Device-token persistence (docs/02-api-spec.md 10장). Plain Exposed DSL, no PostGIS columns --
 * same style as com.dallim.notification.NotificationRepository.
 */
class DeviceTokenRepository(private val database: Database) {

    /**
     * POST /users/me/device-tokens (10.1) -- upsert keyed by fcm_token (globally unique), NOT by
     * (userId, fcmToken): the same physical installation re-registering under a different
     * logged-in account reassigns ownership rather than leaving a stale row under the old user.
     * A re-registration of an already-known token only bumps updatedAt/platform/userId, never
     * inserts a duplicate row.
     */
    fun upsert(userId: String, fcmToken: String, platform: DevicePlatform) {
        transaction(database) {
            val exists = DeviceTokenTable.selectAll()
                .where { DeviceTokenTable.fcmToken eq fcmToken }
                .limit(1)
                .count() > 0

            if (exists) {
                DeviceTokenTable.update({ DeviceTokenTable.fcmToken eq fcmToken }) {
                    it[DeviceTokenTable.userId] = userId
                    it[DeviceTokenTable.platform] = platform
                    it[DeviceTokenTable.updatedAt] = Instant.now()
                }
            } else {
                DeviceTokenTable.insert {
                    it[DeviceTokenTable.id] = IdGenerator.next("dvt")
                    it[DeviceTokenTable.userId] = userId
                    it[DeviceTokenTable.fcmToken] = fcmToken
                    it[DeviceTokenTable.platform] = platform
                }
            }
        }
    }

    /** docs/02-api-spec.md 10.2 fan-out -- every token currently registered for [userId]. */
    fun findTokensForUser(userId: String): List<String> = transaction(database) {
        DeviceTokenTable.selectAll()
            .where { DeviceTokenTable.userId eq userId }
            .map { it[DeviceTokenTable.fcmToken] }
    }

    /** docs/02-api-spec.md 10.2 -- silently dropped when FCM reports UNREGISTERED/NOT_FOUND.
     * No client-facing delete endpoint exists (10.3 explicitly leaves that out of scope). */
    fun deleteToken(fcmToken: String) {
        transaction(database) {
            // deleteWhere's op lambda is `Table.(ISqlExpressionBuilder) -> Op<Boolean>` -- `eq` is
            // a member of ISqlExpressionBuilder (the parameter, not the Table receiver), so it
            // must be called through `it` explicitly (see com.dallim.user.SavedRouteRepository
            // .unsave for the same pattern).
            DeviceTokenTable.deleteWhere { it.run { DeviceTokenTable.fcmToken eq fcmToken } }
        }
    }
}
