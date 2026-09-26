package com.dallim.network.trainingplan

import com.dallim.network.common.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * docs/02-api-spec.md 19장 — S-86 대회 목표 훈련 플랜 (2026-09-18, CLAUDE.md 2026-09-18 결정으로
 * PART 8 로드맵보다 앞당겨 구현, RUN+ 결제 게이트 없이 전면 무료). 백엔드 Kotlin 패키지가
 * `com.dallim.trainingplan`으로 `com.dallim.race`(16장, 대회 캘린더)와 분리돼 있어 클라이언트도
 * 같은 경계로 `RaceApi`에 합치지 않고 별도 인터페이스로 둔다.
 *
 * 실제 생성(주차별 스케줄/코스 매칭/LLM 코멘트)은 Kotlin 백엔드가 아니라 별도 워커
 * (`worker/trainingplan_main.py`)가 비동기로 처리한다 — `POST` 직후 `PENDING`으로 응답이 올 수
 * 있으니 Android는 `GET`을 폴링해서 `READY`/`FAILED` 전환을 감지해야 한다(19.3).
 */
interface TrainingPlanApi {
    /**
     * S-86 훈련 플랜 생성 요청 — 이 대회를 먼저 담아두지 않았으면 `400
     * TRAINING_PLAN_RACE_NOT_SAVED`. 대회 종목이 여럿이면 `category`가 필수
     * (`400/422 TRAINING_PLAN_CATEGORY_REQUIRED`, 이 대회가 제공하지 않는 종목이면
     * `400 TRAINING_PLAN_CATEGORY_NOT_OFFERED`). 이미 `PENDING`/`READY` 행이 있으면 그대로
     * 반환하고 재발행하지 않는다 — 이 엔드포인트는 멱등에 가깝게 동작한다.
     */
    @POST("races/{raceId}/training-plan")
    suspend fun requestGeneration(
        @Path("raceId") raceId: String,
        @Body request: TrainingPlanGenerateRequestBody,
    ): Response<ApiResponse<TrainingPlanResponseBody>>

    /**
     * S-86 훈련 플랜 조회 — `POST`를 한 번도 호출하지 않았으면 `404 TRAINING_PLAN_NOT_FOUND`.
     * Android는 이 신호로 최초 진입 시 생성 요청을 트리거한다(TrainingPlanViewModel.load 참고).
     */
    @GET("races/{raceId}/training-plan")
    suspend fun getTrainingPlan(@Path("raceId") raceId: String): Response<ApiResponse<TrainingPlanResponseBody>>
}

/** `category`는 5K/10K/HALF/FULL/ULTRA/TRAIL — 대회 종목이 하나뿐이면 생략(null) 가능. */
@Serializable
data class TrainingPlanGenerateRequestBody(val category: String? = null)

/**
 * `type`은 LONG_RUN/TEMPO/INTERVAL/REST — 이 앱 전역 관례(RaceSummaryItem.category 등)를 따라
 * Kotlin enum으로 감싸지 않는다. `type == REST`면 targetDistanceKm/routeId류가 전부 null.
 */
@Serializable
data class TrainingPlanSessionItem(
    val sessionIndex: Int,
    val type: String,
    val targetDistanceKm: Double? = null,
    val routeId: String? = null,
    val routeName: String? = null,
    val routeDistanceKm: Double? = null,
)

/**
 * `weeklyTargetDistanceKm`은 그 주 REST가 아닌 세션들의 targetDistanceKm 합(서버가 계산해서
 * 내려준다 — 별도 컬럼 저장 없음). `weekNumber`는 1부터 시작, 마지막 주가 대회 당일 포함 주.
 */
@Serializable
data class TrainingPlanWeekItem(
    val weekNumber: Int,
    val weeklyTargetDistanceKm: Double,
    val sessions: List<TrainingPlanSessionItem> = emptyList(),
)

/**
 * `status`가 `READY`가 아니면 `weeks`는 빈 배열, `comment`/`disclaimer`는 null일 수 있다.
 * `errorMessage`는 `FAILED`일 때만 채워지며 운영/디버깅 참고용이라 사용자에게 그대로 노출하지
 * 않는다(19.3) — Android는 이 값 대신 고정 안내 문구 + "다시 시도"를 보여준다.
 */
@Serializable
data class TrainingPlanResponseBody(
    val planId: String,
    val raceId: String,
    val category: String,
    val status: String,
    val weeks: List<TrainingPlanWeekItem> = emptyList(),
    val comment: String? = null,
    val disclaimer: String? = null,
    val errorMessage: String? = null,
)
