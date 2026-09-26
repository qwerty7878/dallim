package com.dallim.trainingplan

import com.dallim.race.RaceCategory
import com.dallim.race.RaceTable
import com.dallim.route.SketchRouteTable
import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * 대회 목표 훈련 플랜(S-86) — `docs/달림_화면별_상세기획서_v1.3.md` S-86,
 * `docs/02-api-spec.md` 19장 근거. `CLAUDE.md` 2026-09-18 결정으로 PART 8 로드맵(MVP5권)보다
 * 앞당겨 구현하며, RUN+ 결제 게이트 없이 전면 무료로 공개한다.
 *
 * **언어 경계**: 이 도메인은 신고 트리아지 파이프라인(`com.dallim.moderation`)과 동일한 원칙을
 * 따른다 — Kotlin은 [TrainingPlanQueue]로 Redis Stream에 job을 발행/재발행만 하고, 실제
 * 이력 분석 -> 주차별 플랜 생성 -> 코스 매칭 -> LLM 개인화 코멘트는 전부 별도 프로세스인
 * `worker/trainingplan_main.py`(Python + LangGraph)가 수행한다. Kotlin은 OpenAI/LangGraph의
 * 존재를 전혀 모른다.
 *
 * 신고 트리아지와의 차이: 신고는 fire-and-forget(운영자 알림용, Kotlin이 결과를 다시 읽지
 * 않음)이지만, 이 도메인은 유저가 `GET`으로 결과를 폴링해야 하므로 **read-back 경로**가
 * 필요하다 — 그래서 상태([TrainingPlanStatus])를 DB 컬럼으로 갖고, 워커가 실패해도 반드시
 * `FAILED`로 마무리해야 한다(그래야 `GET`이 영원히 `PENDING`에 머무르지 않는다).
 */
enum class TrainingPlanStatus {
    PENDING,
    READY,
    FAILED,
}

/** 세션 타입 — 작업 브리핑 지시대로 롱런/템포/인터벌/휴식 4종만. SPEC에 그 이상의 세분화가
 * 없어 임의로 늘리지 않는다. */
enum class TrainingSessionType {
    LONG_RUN,
    TEMPO,
    INTERVAL,
    REST,
}

/**
 * `training_plans` — 대회(및 유저가 고른 종목) 하나당 최대 한 행. `(user_id, race_id)`
 * unique — 재요청 시 새 행을 만들지 않고 기존 행을 재사용/갱신한다
 * (com.dallim.trainingplan.TrainingPlanService.requestGeneration 참고).
 *
 * [comment]는 LLM이 생성한 개인화 코멘트(READY일 때만 존재) — 컴플라이언스 면책 문구는
 * 절대 이 컬럼에 섞지 않는다. 면책 문구는 변동성이 있으면 안 되므로
 * [TrainingPlanDisclaimer.TEXT] 상수로 Kotlin이 항상 고정 반환한다(LLM 생성 금지).
 */
object TrainingPlanTable : Table("training_plans") {
    val id = varchar("id", 32)
    val userId = varchar("user_id", 32).references(UserTable.id)
    val raceId = varchar("race_id", 32).references(RaceTable.id)
    val category = enumerationByName("category", 16, RaceCategory::class)
    val status = enumerationByName("status", 16, TrainingPlanStatus::class).default(TrainingPlanStatus.PENDING)

    // 워커(personalize_comment_node)가 채운다. READY가 아니면 항상 null.
    val comment = text("comment").nullable()
    // 워커가 그래프 실행 중 예외를 잡아 기록한다(FAILED일 때만 존재) — GET 응답에는 노출하되
    // 사용자에게 그대로 보여주기보다 운영/디버깅 참고용에 가깝다.
    val errorMessage = text("error_message").nullable()

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

data class TrainingPlan(
    val id: String,
    val userId: String,
    val raceId: String,
    val category: RaceCategory,
    val status: TrainingPlanStatus,
    val comment: String?,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * `training_plan_sessions` — 플랜 하나의 주차별 세션들. 별도 "주간 목표 거리" 컬럼은 두지 않고
 * 응답을 만들 때 [TrainingPlanTable]과 조인한 세션들을 `weekNumber`로 묶어 REST를 제외한
 * `targetDistanceKm` 합으로 계산한다(com.dallim.trainingplan.TrainingPlanService 참고) — 별도
 * 컬럼을 두면 세션이 갱신될 때 합계가 어긋날 수 있어 파생값을 저장하지 않는다.
 *
 * [matchedRouteId]는 워커의 `match_courses_node`가 목표 거리와 가장 가까운 공개
 * `sketch_routes`(is_preview_segment=false)를 골라 채운다 — 후보 코스가 하나도 없으면
 * null(REST 세션은 애초에 null).
 */
object TrainingPlanSessionTable : Table("training_plan_sessions") {
    val id = varchar("id", 32)
    val planId = varchar("plan_id", 32).references(TrainingPlanTable.id)
    val weekNumber = integer("week_number")
    val sessionIndex = integer("session_index")
    val type = enumerationByName("type", 16, TrainingSessionType::class)
    val targetDistanceKm = double("target_distance_km").nullable()
    val matchedRouteId = varchar("matched_route_id", 32).references(SketchRouteTable.id).nullable()

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

data class TrainingPlanSession(
    val id: String,
    val planId: String,
    val weekNumber: Int,
    val sessionIndex: Int,
    val type: TrainingSessionType,
    val targetDistanceKm: Double?,
    val matchedRouteId: String?,
    val matchedRouteName: String?,
    val matchedRouteDistanceKm: Double?,
)

/**
 * 컴플라이언스 면책 문구 — 절대 LLM이 생성하게 하지 않는다(작업 브리핑 지시). 문구가
 * 바뀔 일이 있으면 이 상수만 고치면 모든 응답에 즉시 반영된다.
 */
object TrainingPlanDisclaimer {
    const val TEXT =
        "이 훈련 플랜은 일반적인 러닝 코칭 통념에 기반해 자동 생성된 참고용 콘텐츠이며 의학적 " +
            "조언이 아닙니다. 통증이나 몸 상태 이상이 느껴지면 즉시 훈련을 중단하고 전문가와 " +
            "상담하세요."
}
