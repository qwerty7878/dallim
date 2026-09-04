package com.dallim.notification

import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * In-app notification (docs/02-api-spec.md 9장, docs/01-feature-spec.md 1.7 S-46).
 *
 * Phase 1 only ever creates RUN_COMPLETED notifications, from
 * com.dallim.run.RunService.finishRun right next to FinisherCountSync.recordFinisher
 * (docs/02-api-spec.md 9.4). Phone system push (FCM) and any other trigger (nearby new route,
 * marketing, ...) are explicitly out of scope for this round (9.5) — do not add more values here
 * without a corresponding SPEC update.
 */
enum class NotificationType {
    RUN_COMPLETED,
}

object NotificationTable : Table("notifications") {
    val id = varchar("id", 32)
    val userId = varchar("user_id", 32).references(UserTable.id)
    val type = enumerationByName("type", 32, NotificationType::class)
    val title = varchar("title", 100)
    val body = varchar("body", 255)
    val relatedRunId = varchar("related_run_id", 32).nullable()
    val isRead = bool("is_read").default(false)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

data class Notification(
    val id: String,
    val userId: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val relatedRunId: String?,
    val isRead: Boolean,
    val createdAt: Instant,
)
