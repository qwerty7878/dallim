package com.dallim.run

import org.koin.dsl.module

/**
 * Koin module for the run domain — the core module (GPS batch upload, finish judgement,
 * Haversine/coverage/Fréchet calculation) — see docs/01-feature-spec.md 2.2.D and
 * docs/02-api-spec.md 5장. The finish judgement algorithm (RunJudgementService) is the server's
 * sole source of truth (CLAUDE.md rule 3) — never let a client-sent status override it.
 *
 * FinisherCountSync reuses the shared Redis connection + Database beans registered in
 * plugins/Koin.kt's coreModule (see RedisFactory). Its periodic flushToDatabase() is started from
 * com.dallim.Application, not here — Koin modules only wire dependencies, they don't start jobs.
 *
 * RunService also depends on com.dallim.notification.NotificationService (loaded via
 * notificationModule, registered alongside this module in plugins/Koin.kt) to fire the
 * RUN_COMPLETED in-app notification right next to FinisherCountSync.recordFinisher
 * (docs/02-api-spec.md 9.4), and on com.dallim.route.RouteRepository (loaded via routeModule) for
 * the "자유 러닝을 코스로 등록"(2026-09-26) flow — same cross-domain-at-the-service-layer
 * convention as com.dallim.user.SavedRouteService.
 */
val runModule = module {
    single { RunRepository(get(), get()) }
    single { RunJudgementService() }
    single { FinisherCountSync(get(), get()) }
    single { RunService(get(), get(), get(), get(), get()) }
}
