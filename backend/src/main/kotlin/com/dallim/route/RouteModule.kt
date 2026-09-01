package com.dallim.route

import org.koin.dsl.module

/**
 * Koin module for the route domain (curated SketchRoute listing/detail, PostGIS radius search,
 * plus GET /home which is mostly route data — see HomeService). See docs/02-api-spec.md 3-4장
 * and docs/01-feature-spec.md 2.2.C.
 *
 * RouteStatusUpdateJob (docs/01-feature-spec.md 2.3, daily DISCOVERY->VERIFIED->POPULAR batch)
 * reads the `finisher_count` column this module's routes expose. Redis-INCR-backed finisherCount
 * concurrency handling itself lives in com.dallim.run.FinisherCountSync; RouteService depends on
 * com.dallim.run.RunRepository only to source GET /routes/{routeId}/finishers from completed
 * RunRecords. RouteStatusUpdateJob's periodic loop is started from com.dallim.Application (same
 * convention as FinisherCountSync's own start-up) — Koin modules only wire dependencies, they
 * don't start jobs.
 */
val routeModule = module {
    single { RouteRepository(get(), get()) }
    single { RouteService(get(), get()) }
    single { HomeService(get()) }
    single { RouteStatusUpdateJob(get()) }
}
