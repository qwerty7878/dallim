package com.dallim.user

import org.koin.dsl.module

/**
 * Koin module for the user domain — saved-routes (SavedRouteService/SavedRouteRepository) plus
 * profile/nickname-check (UserService/UserRepository), see docs/02-api-spec.md 2장.
 * Reminder: gender is never mapped onto a response DTO (CLAUDE.md rule 2).
 *
 * SavedRouteService additionally depends on com.dallim.run.RunRepository (loaded via runModule)
 * to compute saved-routes' `hasRun`, and com.dallim.route.RouteRepository (loaded via
 * routeModule) for `thumbnailGeoJson` — both registered alongside this module in
 * plugins/Koin.kt, same cross-domain-at-the-service-layer convention as com.dallim.route.HomeService.
 */
val userModule = module {
    single { SavedRouteRepository(get()) }
    single { SavedRouteService(get(), get(), get()) }
    single { UserRepository(get()) }
    single { UserService(get()) }
    // 유저 차단(18장, 사용자 지시로 신규 도입) -- 채팅 메시지 발신자 차단 CRUD만, 세션 신청/매칭
    // 파급 효과 없음.
    single { BlockedUserRepository(get()) }
    single { BlockedUserService(get(), get()) }
}
