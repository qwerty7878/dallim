package com.dallim.push

import org.slf4j.LoggerFactory

/**
 * docs/02-api-spec.md 10.2 -- fan a push out to every device registered for a user. Called from
 * com.dallim.notification.NotificationService right after an in-app notification record is
 * committed; nothing here is allowed to affect that record -- see
 * NotificationService.pushToDevices, which wraps this in its own runCatching as a second line of
 * defense on top of the per-token catch below.
 */
open class FcmPushService(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val fcmSender: FcmSender,
) {
    private val logger = LoggerFactory.getLogger(FcmPushService::class.java)

    /** `open` so com.dallim.notification.NotificationServiceTest can fake it without a real DB
     * or FcmSender, same style as com.dallim.common.OsrmClient being faked in
     * DiscoveryServiceTest. */
    open fun sendToUser(userId: String, title: String, body: String) {
        val tokens = deviceTokenRepository.findTokensForUser(userId)
        for (token in tokens) {
            val result = runCatching { fcmSender.send(token, title, body) }
                .getOrElse {
                    logger.warn("FCM send threw for a token (userId={})", userId, it)
                    FcmSendResult.OtherFailure
                }

            // docs/02-api-spec.md 10.2 -- an invalid token is quietly cleaned up server-side;
            // there is no client-facing delete endpoint (10.3).
            if (result == FcmSendResult.TokenInvalid) {
                deviceTokenRepository.deleteToken(token)
            }
        }
    }
}
