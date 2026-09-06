package com.dallim.race

import org.koin.dsl.module

/**
 * Koin module for the race-calendar(대회 캘린더) domain — docs/02-api-spec.md 16장.
 * No cross-domain dependency — RaceService only needs its own repository (unlike
 * com.dallim.meetup.MeetupModule, which reuses com.dallim.route.RouteRepository).
 */
val raceModule = module {
    single { RaceRepository(get()) }
    single { RaceService(get()) }
}
