package com.dallim.discovery

import com.dallim.common.OsrmClient
import com.dallim.plugins.DallimConfig
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module for the discovery domain (draw-convert / AI discovery course generation —
 * docs/02-api-spec.md 8장). OsrmClient lives in com.dallim.common (reusable OSRM HTTP client,
 * same pattern as auth's GoogleAuthClient/KakaoAuthClient) but is wired here since discovery is
 * currently its only consumer.
 *
 * Two OsrmClient instances are registered (docs/02-api-spec.md 13.2): the default one for
 * LOOP/POINT_TO_POINT/requiredWaypoints, and a "shapeOsrm"-qualified one — pointed at
 * dallim.osrm.shapeBaseUrl, a separate instance/profile — that DiscoveryService only uses for
 * mode: "SHAPE".
 *
 * DiscoveryQuotaService (docs/02-api-spec.md 8.4, daily free-generation quota) reuses the shared
 * Redis connection bean registered in plugins/Koin.kt's coreModule (same pattern as
 * com.dallim.run.FinisherCountSync / com.dallim.auth.RefreshTokenStore) — no DB migration.
 */
val discoveryModule = module {
    single { OsrmClient(get(), get<DallimConfig>().osrm.baseUrl) }
    single(named("shapeOsrm")) { OsrmClient(get(), get<DallimConfig>().osrm.shapeBaseUrl) }
    single { DiscoveryQuotaService(get()) }
    single { DiscoveryService(osrmClient = get(), osrmShapeClient = get(named("shapeOsrm")), quotaService = get()) }
}
