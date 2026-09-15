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
    // 4번째 인자(SocialSessionChatRepository)는 socialSessionChatModule이 등록한다 -- Koin은
    // 같은 modules() 호출에 실린 모든 모듈을 한꺼번에 봐서 등록 순서와 무관하게 해석한다
    // (plugins/Koin.kt 참고).
    single { SocialSessionService(get(), get(), get(), get()) }
}
