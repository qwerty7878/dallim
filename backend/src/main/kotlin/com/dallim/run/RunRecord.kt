package com.dallim.run

import com.dallim.route.SketchRouteTable
import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * Union of every run lifecycle value referenced in docs/02-api-spec.md 5장:
 * - POST /runs                -> IN_PROGRESS
 * - PATCH /runs/{id}/status   -> RUNNING | PAUSED
 * - POST /runs/{id}/finish    -> COMPLETED | PARTIAL | ABORTED | UNDER_REVIEW
 * Server-computed only (docs/01-feature-spec.md 2.2.D) — never trust a client-sent status.
 */
enum class RunStatus {
    IN_PROGRESS,
    RUNNING,
    PAUSED,
    COMPLETED,
    PARTIAL,
    ABORTED,
    UNDER_REVIEW,
}

/**
 * RunRecord — one running session against a SketchRoute.
 *
 * `actualPath` (the GPS track actually run) is a PostGIS `geography(LineString, 4326)` column
 * created in the Flyway migration and intentionally NOT declared here — see
 * com.dallim.common.PostGis. It is populated by the finish-judgement step from the raw
 * GpsPointTable rows (Douglas-Peucker simplified per docs/01-feature-spec.md 2.4), not written
 * directly by request handlers.
 */
object RunRecordTable : Table("run_records") {
    val id = varchar("id", 32)
    val userId = varchar("user_id", 32).references(UserTable.id)

    // 2026-09-26부터 nullable — 자유 러닝(코스 미선택 후 바로 달리기, 사용자 요청으로 신규 편입)은
    // 목표 코스가 없다. null이면 com.dallim.run.RunJudgementService.judgeFreeform이 판정을
    // 맡는다(구간 커버리지/Sketch Match 없이 거리·시간·페이스·비정상속도만).
    val routeId = varchar("route_id", 32).references(SketchRouteTable.id).nullable()

    // "SOLO" is the only mode present in docs/02-api-spec.md; kept as free-form string rather
    // than a closed enum since the full mode set isn't specified yet.
    val mode = varchar("mode", 16).default("SOLO")
    val status = enumerationByName("status", 16, RunStatus::class).default(RunStatus.IN_PROGRESS)

    val startedAt = timestamp("started_at")
    val finishedAt = timestamp("finished_at").nullable()

    // Judgement outputs (docs/01-feature-spec.md 2.2.D) — all server-computed, null until finish.
    val distanceKm = double("distance_km").nullable()
    val durationSeconds = integer("duration_seconds").nullable()
    val averagePaceSecPerKm = integer("average_pace_sec_per_km").nullable()
    val sketchMatchPercent = integer("sketch_match_percent").nullable()
    val routeCompletionPercent = integer("route_completion_percent").nullable()
    val isFirstDiscoverer = bool("is_first_discoverer").default(false)
    val earnedInk = integer("earned_ink").default(0)

    // 2026-09-06 (docs/달림_화면별_상세기획서_v1.3.md PART 4.1) — storage-only for now, collected
    // ahead of a future "step count vs. distance" anti-cheat signal. Never read by
    // RunJudgementService. Nullable: not every device/session can supply a step sensor reading.
    val stepCount = integer("step_count").nullable()

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

data class RunRecord(
    val id: String,
    val userId: String,
    val routeId: String?,
    val mode: String,
    val status: RunStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val distanceKm: Double?,
    val durationSeconds: Int?,
    val averagePaceSecPerKm: Int?,
    val sketchMatchPercent: Int?,
    val routeCompletionPercent: Int?,
    val isFirstDiscoverer: Boolean,
    val earnedInk: Int,
    val stepCount: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
