package com.dallim.notification

import kotlinx.serialization.Serializable

// Response DTOs — docs/02-api-spec.md 9장 (in-app notification center, S-46, phase 1).

@Serializable
data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val relatedRunId: String?,
    val isRead: Boolean,
    val createdAt: String,
)

/** GET /notifications — same items/totalCount/page/size shape as GET /routes. */
@Serializable
data class NotificationListResponse(
    val items: List<NotificationItem>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
)

/** GET /notifications/unread-count — bell icon badge, kept separate from the list endpoint
 * so the client doesn't have to fetch the full page just to render a badge count. */
@Serializable
data class UnreadCountResponse(
    val unreadCount: Int,
)
