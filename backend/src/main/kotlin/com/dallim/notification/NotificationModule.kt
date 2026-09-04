package com.dallim.notification

import org.koin.dsl.module

/**
 * Koin module for the notification domain — docs/02-api-spec.md 9장 (인앱) + 10장 (FCM 푸시).
 * NotificationService additionally depends on com.dallim.push.FcmPushService (registered by
 * com.dallim.push.pushModule) to fan a push out on every notification it creates. RunModule's
 * RunService additionally depends on NotificationService directly (to call notifyRunCompleted
 * from finishRun) — see com.dallim.run.RunModule.
 */
val notificationModule = module {
    single { NotificationRepository(get()) }
    single { NotificationService(get(), get()) }
}
