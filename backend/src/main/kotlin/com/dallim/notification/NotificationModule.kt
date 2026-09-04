package com.dallim.notification

import org.koin.dsl.module

/**
 * Koin module for the notification domain — docs/02-api-spec.md 9장 (인앱 알림함). RunModule's
 * RunService additionally depends on NotificationService directly (to call notifyRunCompleted
 * from finishRun) — see com.dallim.run.RunModule.
 */
val notificationModule = module {
    single { NotificationRepository(get()) }
    single { NotificationService(get()) }
}
