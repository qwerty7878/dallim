package com.dallim.trainingplan

import com.dallim.common.BadRequestException
import com.dallim.common.ErrorCodes
import com.dallim.common.IdGenerator
import com.dallim.common.NotFoundException
import com.dallim.common.UnprocessableEntityException
import com.dallim.race.RaceCategory
import com.dallim.race.RaceCategoryOption
import com.dallim.race.RaceRepository

/**
 * 대회 목표 훈련 플랜(S-86) 비즈니스 로직 — docs/02-api-spec.md 19장. RUN+ 결제 게이트는
 * 넣지 않는다(CLAUDE.md 2026-09-18 결정, 전면 무료).
 *
 * `com.dallim.race`(대회 캘린더)에 의존한다 — "이 대회가 유저의 담아둔 대회여야 한다"는
 * `RaceRepository.isSaved`로, 종목별 정보는 `RaceRepository.findCategoriesByRace`로 재사용한다
 * (com.dallim.race.raceModule이 이미 싱글턴으로 등록한 RaceRepository를 그대로 주입받는다 —
 * com.dallim.race.RaceModule이 RouteRepository/RunRepository를 재사용하는 것과 동일한 관례).
 *
 * 실제 플랜 생성(주차별 스케줄/코스 매칭/LLM 코멘트)은 이 클래스가 전혀 모른다 — PENDING 행을
 * 만들고 [TrainingPlanQueue]에 발행하는 것까지만 하고, `GET`은 워커가 채운 결과를 읽기만 한다.
 */
class TrainingPlanService(
    private val trainingPlanRepository: TrainingPlanRepository,
    private val raceRepository: RaceRepository,
    private val trainingPlanQueue: TrainingPlanQueue,
) {

    /**
     * POST /races/{raceId}/training-plan. 기존 PENDING/READY 행이 있으면 그대로 재사용하고
     * 재발행하지 않는다(중복 생성/중복 job 방지, 작업 브리핑 지시) — 종목을 바꿔서 다시 만들고
     * 싶으면 이번 라운드 범위 밖(재생성 API는 다음 라운드). FAILED였던 행만 PENDING으로 되돌려
     * 재시도한다.
     */
    fun requestGeneration(userId: String, raceId: String, categoryRaw: String?): TrainingPlanResponse {
        val race = raceRepository.findById(raceId)
            ?: throw NotFoundException(ErrorCodes.RACE_NOT_FOUND, "대회를 찾을 수 없습니다.")

        if (!raceRepository.isSaved(userId, raceId)) {
            throw BadRequestException(
                ErrorCodes.TRAINING_PLAN_RACE_NOT_SAVED,
                "먼저 이 대회를 담아야 훈련 플랜을 만들 수 있어요.",
            )
        }

        val categoryOptions = raceRepository.findCategoriesByRace(raceId)
        if (categoryOptions.isEmpty()) {
            throw UnprocessableEntityException(
                ErrorCodes.TRAINING_PLAN_CATEGORY_REQUIRED,
                "이 대회엔 등록된 종목이 없어 훈련 플랜을 만들 수 없어요.",
            )
        }
        val resolvedCategory = resolveCategory(categoryRaw, categoryOptions)

        val existing = trainingPlanRepository.findByUserAndRace(userId, raceId)
        val plan = when {
            existing == null -> {
                val created = trainingPlanRepository.createPending(IdGenerator.trainingPlan(), userId, raceId, resolvedCategory)
                trainingPlanQueue.publish(created.id, userId, raceId, resolvedCategory.name, race.raceDate.toString())
                created
            }
            existing.status == TrainingPlanStatus.FAILED -> {
                trainingPlanRepository.resetToPending(existing.id, resolvedCategory)
                trainingPlanQueue.publish(existing.id, userId, raceId, resolvedCategory.name, race.raceDate.toString())
                existing.copy(
                    category = resolvedCategory,
                    status = TrainingPlanStatus.PENDING,
                    comment = null,
                    errorMessage = null,
                )
            }
            // PENDING(이미 처리 중) / READY(이미 완료) — 그대로 반환, 재발행하지 않는다.
            else -> existing
        }

        val sessions = if (plan.status == TrainingPlanStatus.READY) {
            trainingPlanRepository.findSessionsByPlan(plan.id)
        } else {
            emptyList()
        }
        return plan.toResponse(sessions)
    }

    /** GET /races/{raceId}/training-plan. POST를 먼저 호출하지 않았으면 404. */
    fun getPlan(userId: String, raceId: String): TrainingPlanResponse {
        val plan = trainingPlanRepository.findByUserAndRace(userId, raceId)
            ?: throw NotFoundException(
                ErrorCodes.TRAINING_PLAN_NOT_FOUND,
                "아직 만든 훈련 플랜이 없어요. 먼저 생성해주세요.",
            )
        val sessions = if (plan.status == TrainingPlanStatus.READY) {
            trainingPlanRepository.findSessionsByPlan(plan.id)
        } else {
            emptyList()
        }
        return plan.toResponse(sessions)
    }

    /** category 파라미터가 없고 종목이 하나뿐이면 그 하나로 자동 결정, 여러 개면 필수. 잘못된
     * enum 값/이 대회가 제공하지 않는 종목이면 400. com.dallim.race.RaceService.parseCategoryOrNull과
     * 동일한 매핑(그쪽은 private이라 여기서 다시 둔다 — 두 도메인이 같은 RaceCategory enum을
     * 문자열로 주고받는 지점이 서로 달라 공유 유틸로 뽑을 만큼 크지 않다). */
    private fun resolveCategory(raw: String?, options: List<RaceCategoryOption>): RaceCategory {
        if (raw != null) {
            val parsed = parseCategoryOrNull(raw)
                ?: throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "올바르지 않은 종목이에요.")
            if (options.none { it.category == parsed }) {
                throw BadRequestException(
                    ErrorCodes.TRAINING_PLAN_CATEGORY_NOT_OFFERED,
                    "이 대회에서 제공하지 않는 종목이에요.",
                )
            }
            return parsed
        }
        if (options.size == 1) return options[0].category
        throw BadRequestException(
            ErrorCodes.TRAINING_PLAN_CATEGORY_REQUIRED,
            "이 대회는 종목이 여러 개예요. 준비할 종목을 알려주세요.",
        )
    }

    private fun parseCategoryOrNull(raw: String): RaceCategory? = when (raw) {
        "5K" -> RaceCategory.FIVE_K
        "10K" -> RaceCategory.TEN_K
        "HALF" -> RaceCategory.HALF
        "FULL" -> RaceCategory.FULL
        "ULTRA" -> RaceCategory.ULTRA
        "TRAIL" -> RaceCategory.TRAIL
        else -> null
    }

    /** [sessions]를 weekNumber로 묶어 응답 모양으로 변환. weeklyTargetDistanceKm은 저장된 컬럼이
     * 아니라 REST를 제외한 그 주 세션들의 targetDistanceKm 합(TrainingPlanSessionTable 문서 참고). */
    private fun TrainingPlan.toResponse(sessions: List<TrainingPlanSession>): TrainingPlanResponse {
        val weeks = sessions
            .groupBy { it.weekNumber }
            .toSortedMap()
            .map { (weekNumber, weekSessions) ->
                val sorted = weekSessions.sortedBy { it.sessionIndex }
                TrainingPlanWeekResponse(
                    weekNumber = weekNumber,
                    // 소수 첫째 자리로 반올림 — 그렇지 않으면 IEEE754 덧셈 오차가 그대로 응답에
                    // 노출된다(예: 5.6 + 2.8 == 8.399999999999999, Android 실기기 검증 중 발견).
                    weeklyTargetDistanceKm = sorted
                        .filter { it.type != TrainingSessionType.REST }
                        .sumOf { it.targetDistanceKm ?: 0.0 }
                        .let { Math.round(it * 10) / 10.0 },
                    sessions = sorted.map {
                        TrainingPlanSessionResponse(
                            sessionIndex = it.sessionIndex,
                            type = it.type,
                            targetDistanceKm = it.targetDistanceKm,
                            routeId = it.matchedRouteId,
                            routeName = it.matchedRouteName,
                            routeDistanceKm = it.matchedRouteDistanceKm,
                        )
                    },
                )
            }

        return TrainingPlanResponse(
            planId = id,
            raceId = raceId,
            category = category,
            status = status,
            weeks = weeks,
            comment = comment,
            // 면책 문구는 LLM이 아니라 Kotlin이 고정 상수로 붙인다(컴플라이언스 문구는 변동성이
            // 있으면 안 됨, 작업 브리핑 지시) — READY일 때만 의미가 있어 그 외엔 null.
            disclaimer = if (status == TrainingPlanStatus.READY) TrainingPlanDisclaimer.TEXT else null,
            errorMessage = errorMessage,
        )
    }
}
