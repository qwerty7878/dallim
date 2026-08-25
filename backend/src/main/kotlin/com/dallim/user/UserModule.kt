package com.dallim.user

import org.koin.dsl.module

/**
 * Koin module for the user domain. This round only wires saved-routes (SavedRouteService /
 * SavedRouteRepository) — see docs/02-api-spec.md 2장. Profile registration, GET /users/me,
 * and nickname-check are a separate user-domain round; when that lands, register
 * UserService / UserRepository here too.
 * Reminder: gender is never mapped onto a response DTO (CLAUDE.md rule 2).
 */
val userModule = module {
    single { SavedRouteRepository(get()) }
    single { SavedRouteService(get()) }
}
