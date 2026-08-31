package com.dallim.dallimbook

import org.koin.dsl.module

/**
 * Koin module for the dallimbook domain — docs/02-api-spec.md 6장, a single endpoint
 * (GET /users/me/runs). DallimbookRepository takes the shared DataSource bean directly (not the
 * Exposed Database bean) because it reads `run_records.actual_path`, a PostGIS geography column
 * Exposed can't express — see DallimbookRepository's class doc and com.dallim.common.PostGis.
 */
val dallimbookModule = module {
    single { DallimbookRepository(get()) }
    single { DallimbookService(get()) }
}
