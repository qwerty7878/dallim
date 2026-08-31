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
 */
val runModule = module {
    single { RunRepository(get(), get()) }
    single { RunJudgementService() }
    single { FinisherCountSync(get(), get()) }
    single { RunService(get(), get(), get()) }
}
