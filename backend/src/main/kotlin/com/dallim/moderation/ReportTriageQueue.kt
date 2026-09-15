package com.dallim.moderation

import com.dallim.social.ContentReportTargetType
import io.lettuce.core.api.StatefulRedisConnection

/**
 * `content_reports` INSERT 직후 Redis Stream에 트리아지 job을 발행한다 (신고 카테고리/심각도
 * 자동 분류 + Discord 알림 파이프라인, 사용자 지시로 신규 도입 -- docs/02-api-spec.md 17.18에
 * "신고에 대한 자동 조치/모더레이션 큐는 범위 밖"이라 적혀 있던 것을 이번에 뒤집는다).
 *
 * 실제 분류(OpenAI)/알림(Discord 웹훅)은 별도 프로세스인 `worker/`(Python + LangGraph)가
 * 이 스트림을 Consumer Group으로 구독해서 처리한다. 이 클래스는 "발행만" 하고 분류 로직을 전혀
 * 모른다 -- 언어 경계를 Redis Stream 하나로 분리해서, Kotlin 백엔드가 Python/LangGraph를 몰라도
 * 되게 한다. 결과는 `content_report_triage` 테이블에 워커가 직접 쓴다(Kotlin은 이 테이블을
 * 아직 읽지 않는다 -- 운영자가 직접 조회하거나, 다음 라운드에서 관리자 화면을 붙인다).
 */
class ReportTriageQueue(
    private val connection: StatefulRedisConnection<String, String>,
) {
    companion object {
        const val STREAM_KEY = "reports:triage"
    }

    fun publish(
        reportId: String,
        targetType: ContentReportTargetType,
        targetId: String,
        reporterUserId: String,
        reason: String?,
    ) {
        val fields = buildMap {
            put("reportId", reportId)
            put("targetType", targetType.name)
            put("targetId", targetId)
            put("reporterUserId", reporterUserId)
            if (reason != null) put("reason", reason)
        }
        connection.sync().xadd(STREAM_KEY, fields)
    }
}
