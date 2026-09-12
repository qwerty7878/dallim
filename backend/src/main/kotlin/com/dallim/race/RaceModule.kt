package com.dallim.race

import org.koin.dsl.module

/**
 * Koin module for the race-calendar(대회 캘린더) domain — docs/02-api-spec.md 16장.
 *
 * 2026-09-12(S-85, 대회 코스 미리 달리기)부터는 더 이상 "no cross-domain dependency"가 아니다
 * — RaceService가 공식 코스 요약(`com.dallim.route.RouteRepository`)과 구간 완주 여부
 * (`com.dallim.run.RunRepository`)를 함께 조회해야 하기 때문. 두 레포지토리 모두 각자의
 * 모듈(routeModule/runModule)이 이미 싱글턴으로 등록해두므로 여기서 새로 만들지 않고
 * `get()`으로 재사용한다(com.dallim.meetup.MeetupModule이 RouteRepository를 재사용하는 것과
 * 동일한 패턴, com.dallim.user.UserModule의 SavedRouteService가 RunRepository를 재사용하는
 * 것과도 동일한 패턴).
 */
val raceModule = module {
    single { RaceRepository(get()) }
    single { RaceService(get(), get(), get()) }
}
