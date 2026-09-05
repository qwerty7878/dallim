package com.dallim.notification

import com.dallim.common.ErrorCodes
import com.dallim.common.NotFoundException
import com.dallim.push.FcmPushService
import org.slf4j.LoggerFactory
import java.util.Locale

/**
 * Notification domain business logic — docs/02-api-spec.md 9장 (인앱 알림함) + 10장 (FCM 푸시,
 * 알림 2단계). NotificationRoutes.kt stays a thin HTTP adapter; [notifyRunCompleted] has no route
 * of its own — it's called from com.dallim.run.RunService.finishRun, right next to
 * FinisherCountSync.recordFinisher (docs/02-api-spec.md 9.4).
 */
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val fcmPushService: FcmPushService,
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

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
     * 문구, body는 "{코스명} {거리}km를 완주했어요." 포맷. 10.2에 따라 인앱 레코드 생성 직후 동일한
     * title/body로 등록된 기기에 FCM 푸시도 발송한다.
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

        pushToDevices(userId, title, body)
    }

    /**
     * docs/02-api-spec.md 14.5, docs/01-feature-spec.md 1.8.2 — fires at the meetup's host when
     * someone else joins their recruiting post. There is no `related_meetup_id` column on
     * `notifications` (only `related_run_id`, from the 9장 phase-1 schema) and SPEC doesn't ask
     * for one here, so [relatedRunId] is left null for this notification type — same 10.2 FCM
     * fan-out as every other notification, no additional wiring needed.
     */
    fun notifyMeetupJoined(hostUserId: String, joinerNickname: String, routeName: String) {
        val title = "모집에 새 참가자가 있어요"
        val body = "${joinerNickname}님이 [$routeName] 모집에 참가했어요"

        notificationRepository.create(
            userId = hostUserId,
            type = NotificationType.MEETUP_JOINED,
            title = title,
            body = body,
            relatedRunId = null,
        )

        pushToDevices(hostUserId, title, body)
    }

    /**
     * docs/02-api-spec.md 10.2 — fires strictly after the in-app record above is committed
     * (NotificationRepository.create's own `transaction {}` has already returned), and is
     * deliberately outside that transaction. A push failure here must never roll back or hide
     * the in-app notification, so every exception is caught and only logged.
     */
    private fun pushToDevices(userId: String, title: String, body: String) {
        runCatching { fcmPushService.sendToUser(userId, title, body) }
            .onFailure { logger.warn("FCM push failed for userId={}", userId, it) }
    }
}
