package com.dallim.trainingplan

import com.dallim.race.RaceCategory
import kotlinx.serialization.Serializable

// POST/GET /races/{raceId}/training-plan — docs/02-api-spec.md 19장. gender는 어디에도 없다
// (CLAUDE.md 규칙 2, 애초에 이 도메인엔 무관).

/** POST /races/{raceId}/training-plan 요청. 대회에 종목이 하나뿐이면 생략 가능 —
 * TrainingPlanService.requestGeneration이 자동으로 그 하나를 쓴다. 여러 종목이면 필수
 * (400 TRAINING_PLAN_CATEGORY_REQUIRED). */
@Serializable
data class TrainingPlanGenerateRequest(
    val category: String? = null,
)

/** GET/POST 공통 응답 세션 아이템. `type == REST`면 targetDistanceKm/routeId류가 전부 null. */
@Serializable
data class TrainingPlanSessionResponse(
    val sessionIndex: Int,
    val type: TrainingSessionType,
    val targetDistanceKm: Double?,
    val routeId: String?,
    val routeName: String?,
    val routeDistanceKm: Double?,
)

/** 주차 하나 — `weeklyTargetDistanceKm`은 그 주 REST가 아닌 세션들의 targetDistanceKm 합
 * (TrainingPlanService에서 계산, 별도 컬럼으로 저장하지 않음). `weekNumber`는 1부터 시작하고
 * 마지막 주가 대회 당일이 포함된 주다. */
@Serializable
data class TrainingPlanWeekResponse(
    val weekNumber: Int,
    val weeklyTargetDistanceKm: Double,
    val sessions: List<TrainingPlanSessionResponse>,
)

/** GET/POST /races/{raceId}/training-plan 공통 응답. `status`가 `READY`가 아니면
 * `weeks`/`comment`/`disclaimer`는 비어있거나 null이다 — 아직 만들어진 플랜이 없어서다.
 * `errorMessage`는 `FAILED`일 때만 채워진다(운영/디버깅 참고용, 사용자 노출 문구가 아닐 수
 * 있음). */
@Serializable
data class TrainingPlanResponse(
    val planId: String,
    val raceId: String,
    val category: RaceCategory,
    val status: TrainingPlanStatus,
    val weeks: List<TrainingPlanWeekResponse>,
    val comment: String?,
    val disclaimer: String?,
    val errorMessage: String?,
)
