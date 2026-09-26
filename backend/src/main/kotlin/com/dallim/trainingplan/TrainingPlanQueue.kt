package com.dallim.trainingplan

import io.lettuce.core.api.StatefulRedisConnection

/**
 * `training_plans` 행이 PENDING(신규 생성 또는 FAILED에서 재시도)이 될 때마다 Redis Stream에
 * 생성 job을 발행한다 — `com.dallim.moderation.ReportTriageQueue`와 정확히 같은 형태의 언어
 * 경계용 클래스다. 실제 이력 분석/플랜 생성/코스 매칭/LLM 코멘트는 전부 별도 프로세스인
 * `worker/trainingplan_main.py`(Python + LangGraph)가 이 스트림을 Consumer Group으로 구독해서
 * 처리한다 — 이 클래스는 "발행만" 하고 그 로직을 전혀 모른다.
 *
 * 신고 트리아지 큐와의 차이: 그쪽은 fire-and-forget이지만, 여기는 유저가 `GET`으로 결과를
 * 기다리므로 워커가 반드시 `training_plans.status`를 `READY` 또는 `FAILED`로 마무리해야
 * 한다(워커 쪽 `trainingplan_main.py` 컨슈머 루프 참고) — 이 발행 자체의 스키마/타이밍에는
 * 영향 없음.
 */
class TrainingPlanQueue(
    private val connection: StatefulRedisConnection<String, String>,
) {
    companion object {
        const val STREAM_KEY = "training-plans:generate"
    }

    fun publish(
        planId: String,
        userId: String,
        raceId: String,
        raceCategory: String,
        raceDateIso: String,
    ) {
        val fields = mapOf(
            "planId" to planId,
            "userId" to userId,
            "raceId" to raceId,
            "raceCategory" to raceCategory,
            "raceDateIso" to raceDateIso,
        )
        connection.sync().xadd(STREAM_KEY, fields)
    }
}
