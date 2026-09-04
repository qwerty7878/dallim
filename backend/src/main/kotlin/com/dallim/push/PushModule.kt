package com.dallim.push

import org.koin.dsl.module

/**
 * Koin module for the FCM push domain (docs/02-api-spec.md 10장, "폰 시스템 푸시").
 * com.dallim.notification.NotificationService (notificationModule) depends on [FcmPushService]
 * to fan a push out to every device registered for a notification's recipient, right after
 * creating the in-app record.
 */
val pushModule = module {
    single { DeviceTokenRepository(get()) }
    single<FcmSender> { FirebaseFcmSender(get()) }
    single { FcmPushService(get(), get()) }
    single { DeviceTokenService(get()) }
}
