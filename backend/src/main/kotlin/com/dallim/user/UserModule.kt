package com.dallim.user

import org.koin.dsl.module

/**
 * Koin module for the user domain — saved-routes (SavedRouteService/SavedRouteRepository) plus
 * profile/nickname-check (UserService/UserRepository), see docs/02-api-spec.md 2장.
 * Reminder: gender is never mapped onto a response DTO (CLAUDE.md rule 2).
 *
 * SavedRouteService additionally depends on com.dallim.run.RunRepository (loaded via runModule,
 * registered alongside this module in plugins/Koin.kt) to compute saved-routes' `hasRun` — same
 * cross-domain-at-the-service-layer convention as com.dallim.route.RouteModule.
 */
val userModule = module {
    single { SavedRouteRepository(get()) }
    single { SavedRouteService(get(), get()) }
    single { UserRepository(get()) }
    single { UserService(get()) }
}
