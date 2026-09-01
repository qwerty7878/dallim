package com.dallim.user

import org.koin.dsl.module

/**
 * Koin module for the user domain — saved-routes (SavedRouteService/SavedRouteRepository) plus
 * profile/nickname-check (UserService/UserRepository), see docs/02-api-spec.md 2장.
 * Reminder: gender is never mapped onto a response DTO (CLAUDE.md rule 2).
 */
val userModule = module {
    single { SavedRouteRepository(get()) }
    single { SavedRouteService(get()) }
    single { UserRepository(get()) }
    single { UserService(get()) }
}
