package com.dallim.racerecord

import org.koin.dsl.module

/**
 * Koin module for the race-record(러닝 커리어/완주 이력) domain — docs/02-api-spec.md 15장.
 * No cross-domain dependency (unlike meetup/savedRoute) — RaceRecordService only needs its own
 * repository.
 */
val raceRecordModule = module {
    single { RaceRecordRepository(get()) }
    single { RaceRecordService(get()) }
}
