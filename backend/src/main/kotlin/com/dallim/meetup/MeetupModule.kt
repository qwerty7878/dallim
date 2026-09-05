package com.dallim.meetup

import org.koin.dsl.module

/**
 * Koin module for the run-together recruiting domain — docs/02-api-spec.md 14장,
 * docs/01-feature-spec.md 1.8 (2026-09-05, scope officially reopened).
 *
 * MeetupService depends on com.dallim.route.RouteRepository (existence checks only, reusing
 * ROUTE_NOT_FOUND per 14.3) and com.dallim.notification.NotificationService (the join-triggered
 * in-app notification, 14.5) — both already registered by routeModule/notificationModule
 * (see plugins/Koin.kt), same cross-domain-dependency convention as com.dallim.run.RunModule.
 */
val meetupModule = module {
    single { MeetupRepository(get()) }
    single { MeetupService(get(), get(), get()) }
}
