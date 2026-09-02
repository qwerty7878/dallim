package com.dallim.discovery

import com.dallim.common.OsrmClient
import com.dallim.plugins.DallimConfig
import org.koin.dsl.module

/**
 * Koin module for the discovery domain (draw-convert / AI discovery course generation —
 * docs/02-api-spec.md 8장). OsrmClient lives in com.dallim.common (reusable OSRM HTTP client,
 * same pattern as auth's GoogleAuthClient/KakaoAuthClient) but is wired here since discovery is
 * currently its only consumer.
 */
val discoveryModule = module {
    single { OsrmClient(get(), get<DallimConfig>().osrm.baseUrl) }
    single { DiscoveryService(get()) }
}
