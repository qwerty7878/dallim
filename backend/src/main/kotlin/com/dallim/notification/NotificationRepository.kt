package com.dallim.notification

import com.dallim.common.IdGenerator
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/**
 * Notification persistence — docs/02-api-spec.md 9장. No PostGIS columns involved, so this is
 * plain Exposed DSL against [database] throughout, unlike com.dallim.route.RouteRepository /
 * com.dallim.run.RunRepository which fall back to raw JDBC for geometry columns.
 */
open class NotificationRepository(private val database: Database) {

    data class NotificationRow(
        val id: String,
        val type: NotificationType,
        val title: String,
        val body: String,
        val relatedRunId: String?,
        val isRead: Boolean,
        val createdAt: Instant,
    )

    /** docs/02-api-spec.md 9.4 트리거 — RunService.finishRun only, no client-facing route.
     * `open` so com.dallim.notification.NotificationServiceTest can fake it without a real DB,
     * same style as com.dallim.common.OsrmClient being faked in DiscoveryServiceTest. */
    open fun create(userId: String, type: NotificationType, title: String, body: String, relatedRunId: String?): String {
        val id = IdGenerator.next("ntf")
        transaction(database) {
            NotificationTable.insert {
                it[NotificationTable.id] = id
                it[NotificationTable.userId] = userId
                it[NotificationTable.type] = type
                it[NotificationTable.title] = title
                it[NotificationTable.body] = body
                it[NotificationTable.relatedRunId] = relatedRunId
                it[NotificationTable.isRead] = false
            }
        }
        return id
    }

    /** GET /notifications — latest first. */
    fun findPage(userId: String, page: Int, size: Int): Pair<List<NotificationRow>, Int> = transaction(database) {
        val totalCount = NotificationTable.selectAll()
            .where { NotificationTable.userId eq userId }
            .count()
            .toInt()

        val items = NotificationTable.selectAll()
            .where { NotificationTable.userId eq userId }
            .orderBy(NotificationTable.createdAt, SortOrder.DESC)
            .limit(size)
            .offset(page.toLong() * size)
            .map { it.toNotificationRow() }

        items to totalCount
    }

    /** GET /notifications/unread-count. */
    fun countUnread(userId: String): Int = transaction(database) {
        NotificationTable.selectAll()
            .where { (NotificationTable.userId eq userId) and (NotificationTable.isRead eq false) }
            .count()
            .toInt()
    }

    /**
     * POST /notifications/{id}/read — returns false when [notificationId] doesn't exist or isn't
     * owned by [userId] (caller maps that to 404 NOTIFICATION_NOT_FOUND). Marking an
     * already-read notification is still a no-op success (idempotent per docs/02-api-spec.md 9.3).
     */
    fun markReadIfOwned(userId: String, notificationId: String): Boolean = transaction(database) {
        val owned = NotificationTable.selectAll()
            .where { (NotificationTable.id eq notificationId) and (NotificationTable.userId eq userId) }
            .limit(1)
            .count() > 0

        if (owned) {
            NotificationTable.update({ NotificationTable.id eq notificationId }) {
                it[NotificationTable.isRead] = true
            }
        }
        owned
    }

    private fun ResultRow.toNotificationRow() = NotificationRow(
        id = this[NotificationTable.id],
        type = this[NotificationTable.type],
        title = this[NotificationTable.title],
        body = this[NotificationTable.body],
        relatedRunId = this[NotificationTable.relatedRunId],
        isRead = this[NotificationTable.isRead],
        createdAt = this[NotificationTable.createdAt],
    )
}
