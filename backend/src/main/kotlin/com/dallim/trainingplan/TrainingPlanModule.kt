package com.dallim.trainingplan

import org.koin.dsl.module

/**
 * Koin module for the training-plan(대회 목표 훈련 플랜, S-86) domain — docs/02-api-spec.md 19장.
 *
 * `RaceRepository`는 com.dallim.race.raceModule이 이미 싱글턴으로 등록해둔 것을 `get()`으로
 * 재사용한다(com.dallim.race.raceModule이 RouteRepository/RunRepository를 재사용하는 것과
 * 동일한 패턴) — 여기서 새로 만들지 않는다.
 */
val trainingPlanModule = module {
    single { TrainingPlanRepository(get()) }
    single { TrainingPlanQueue(get()) }
    single { TrainingPlanService(get(), get(), get()) }
}
