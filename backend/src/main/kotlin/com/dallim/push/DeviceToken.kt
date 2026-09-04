package com.dallim.push

import com.dallim.common.IdGenerator
import com.dallim.user.UserTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * FCM device token registry (docs/02-api-spec.md 10장, docs/01-feature-spec.md 1.7 2단계).
 * ANDROID is the only value in MVP1 scope -- the app is Android-only right now; do not add e.g.
 * IOS speculatively (CLAUDE.md rule 1, no expanding SPEC).
 */
@Serializable
enum class DevicePlatform { ANDROID }

object DeviceTokenTable : Table("device_tokens") {
    val id = varchar("id", 32)
    val userId = varchar("user_id", 32).references(UserTable.id)
    val fcmToken = varchar("fcm_token", 255).uniqueIndex()
    val platform = enumerationByName("platform", 16, DevicePlatform::class)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

/** Row shape mirrors [DeviceTokenTable]; id is generated with [IdGenerator.next] ("dvt_..."). */
data class DeviceToken(
    val id: String,
    val userId: String,
    val fcmToken: String,
    val platform: DevicePlatform,
    val createdAt: Instant,
    val updatedAt: Instant,
)
