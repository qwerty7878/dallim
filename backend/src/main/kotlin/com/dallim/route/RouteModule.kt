package com.dallim.route

import org.koin.dsl.module

/**
 * Koin module for the route domain (curated SketchRoute listing/detail, PostGIS radius search).
 * See docs/02-api-spec.md 4장 and docs/01-feature-spec.md 2.2.C.
 *
 * RouteStatusUpdateJob (docs/01-feature-spec.md 2.3, daily DISCOVERY->VERIFIED->POPULAR batch)
 * and Redis-INCR-backed finisherCount concurrency handling are run-domain-adjacent and out of
 * scope for this round — they land once the run domain's finish-judgement flow exists.
 */
val routeModule = module {
    single { RouteRepository(get(), get()) }
    single { RouteService(get()) }
}
