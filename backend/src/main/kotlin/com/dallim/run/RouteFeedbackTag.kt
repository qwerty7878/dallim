package com.dallim.run

import com.dallim.route.SketchRouteTable
import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * Fixed vocabulary for course feedback tags — docs/01-feature-spec.md 2.2.G,
 * docs/02-api-spec.md 5장 (POST /runs/{runId}/feedback-tags),
 * docs/달림_화면별_상세기획서_v1.3.md 301행(S-16)/395행(S-25)/483~484행.
 *
 * All entries are neutral/positive descriptive phrasing only — the v1.3 문서 원칙("긍정 행동
 * 태그만 선택(별점 없음)", "부정 평가는 태그가 아니라 신고 경로로만 처리(태그로 부정 평가를
 * 만들면 보복 평가가 시작됨)") applies to course tags exactly as written for its original screen.
 * Never add a negative-leaning tag to this set — a client-submitted tag outside [ALLOWED] is a
 * 400 VALIDATION_ERROR (see RunService.submitFeedbackTags), not silently dropped.
 */
object RouteFeedbackTags {
    val ALLOWED: Set<String> = setOf(
        "그림이 잘 보여요",
        "달리기 편해요",
        "신호가 적어요",
        "평지예요",
        "가로등이 밝아요",
        "경치가 좋아요",
    )

    const val MAX_TAGS_PER_SUBMISSION = 3
}

/**
 * route_feedback_tags — one row per (run, tag) a user submitted at the results screen
 * ("3초 컷" 저마찰 UX, v1.3 문서 395행). `(run_id, tag)` is UNIQUE (see
 * V9__route_feedback_tags.sql) so a submission can never duplicate a row within one run; combined
 * with RunRepository.submitFeedbackTagsIfAbsent's "any row already exists for this run -> skip"
 * check, a resubmission is a full no-op rather than an error or an overwrite.
 *
 * GET /routes/{routeId}'s `topFeedbackTags` (RouteService.getDetail) aggregates this table by
 * `route_id` — see RunRepository.findFeedbackTagsByRoute.
 */
object RouteFeedbackTagTable : Table("route_feedback_tags") {
    val id = varchar("id", 32)
    val runId = varchar("run_id", 32).references(RunRecordTable.id)
    val routeId = varchar("route_id", 32).references(SketchRouteTable.id)
    val userId = varchar("user_id", 32).references(UserTable.id)
    val tag = varchar("tag", 32)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}
