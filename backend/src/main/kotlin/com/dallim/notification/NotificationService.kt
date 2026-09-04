package com.dallim.notification

import com.dallim.common.ErrorCodes
import com.dallim.common.NotFoundException
import java.util.Locale

/**
 * Notification domain business logic — docs/02-api-spec.md 9장 (인앱 알림함). NotificationRoutes.kt
 * stays a thin HTTP adapter; [notifyRunCompleted] has no route of its own — it's called from
 * com.dallim.run.RunService.finishRun, right next to FinisherCountSync.recordFinisher
 * (docs/02-api-spec.md 9.4).
 */
class NotificationService(
    private val notificationRepository: NotificationRepository,
) {
    /** GET /notifications. */
    fun listMyNotifications(userId: String, page: Int, size: Int): NotificationListResponse {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)

        val (rows, totalCount) = notificationRepository.findPage(userId, safePage, safeSize)
        val items = rows.map {
            NotificationItem(
                id = it.id,
                type = it.type,
                title = it.title,
                body = it.body,
                relatedRunId = it.relatedRunId,
                isRead = it.isRead,
                createdAt = it.createdAt.toString(),
            )
        }

        return NotificationListResponse(items = items, totalCount = totalCount, page = safePage, size = safeSize)
    }

    /** GET /notifications/unread-count. */
    fun unreadCount(userId: String): UnreadCountResponse =
        UnreadCountResponse(unreadCount = notificationRepository.countUnread(userId))

    /** POST /notifications/{id}/read — idempotent; 404 when the notification doesn't exist or
     * isn't owned by [userId] (hides existence rather than a distinct "forbidden" signal, same
     * convention as com.dallim.run.RunService.findOwnedRun). */
    fun markRead(userId: String, notificationId: String) {
        val owned = notificationRepository.markReadIfOwned(userId, notificationId)
        if (!owned) {
            throw NotFoundException(ErrorCodes.NOTIFICATION_NOT_FOUND, "알림을 찾을 수 없습니다.")
        }
    }

    /**
     * docs/02-api-spec.md 9.4 — 러닝 완주(COMPLETED) 확정 시 1건 생성. title은 스펙 예시 그대로 고정
     * 문구, body는 "{코스명} {거리}km를 완주했어요." 포맷.
     */
    fun notifyRunCompleted(userId: String, runId: String, routeName: String, distanceKm: Double) {
        val title = "완주를 축하드려요! 🎉"
        val body = String.format(Locale.ROOT, "%s %.2fkm를 완주했어요.", routeName, distanceKm)

        notificationRepository.create(
            userId = userId,
            type = NotificationType.RUN_COMPLETED,
            title = title,
            body = body,
            relatedRunId = runId,
        )
    }
}
