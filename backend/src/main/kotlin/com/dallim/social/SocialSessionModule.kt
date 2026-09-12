package com.dallim.social

import org.koin.dsl.module

/**
 * Koin module for the social session domain (S-30~S-34, 1단계) — docs/02-api-spec.md 17장.
 *
 * SocialSessionService depends on com.dallim.route.RouteRepository (route existence + course
 * preview lookups) and com.dallim.user.UserRepository (host/applicant profile + gender/temperature
 * condition checks) — both already registered by routeModule/userModule (see plugins/Koin.kt),
 * same cross-domain-dependency convention as com.dallim.meetup.MeetupModule.
 */
val socialSessionModule = module {
    single { SocialSessionRepository(get()) }
    single { SocialSessionService(get(), get(), get()) }
}
