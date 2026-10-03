package com.dallim.route

import com.dallim.run.RunRecordTable
import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * Route lifecycle — fully enumerated in docs/01-feature-spec.md 2.2.C:
 * DISCOVERY -> (1+ finisher) -> VERIFIED -> (N+ finishers, threshold TBD) -> POPULAR
 * Transition is driven by the daily RouteStatusUpdateJob batch, not by request handlers.
 */
enum class RouteStatus {
    DISCOVERY,
    VERIFIED,
    POPULAR,
}

/**
 * SketchRoute — an operator-curated running route shaped like a drawing when traced by GPS.
 *
 * The route geometry itself (`path`, a PostGIS `geography(LineString, 4326)`) is NOT declared
 * as an Exposed column here — see com.dallim.common.PostGis doc comment for why, and read/write
 * it through raw SQL (ST_AsGeoJSON / ST_GeomFromGeoJSON) instead of Exposed's DSL.
 */
object SketchRouteTable : Table("sketch_routes") {
    val id = varchar("id", 32)
    val name = varchar("name", 50)
    val emoji = varchar("emoji", 8)
    // path: geography(LineString, 4326) — created in Flyway migration, accessed via PostGis util.

    val distanceKm = double("distance_km")
    val estimatedMinutes = integer("estimated_minutes")
    // Free-form per docs/02-api-spec.md example ("EASY") — not fully enumerated in SPEC yet.
    val difficulty = varchar("difficulty", 16).nullable()
    val status = enumerationByName("status", 16, RouteStatus::class).default(RouteStatus.DISCOVERY)

    val finisherCount = integer("finisher_count").default(0)
    val trafficLightCount = integer("traffic_light_count").default(0)
    val elevationGainM = integer("elevation_gain_m").default(0)
    val repeatSegmentPercent = integer("repeat_segment_percent").default(0)
    val runability = double("runability").default(0.0)

    // 대회 코스 미리 달리기(S-85, docs/02-api-spec.md 16.6) 구간용 row 표시 플래그. TRUE인
    // row는 com.dallim.route.RouteRepository.search / findTodaySketchCandidate가 WHERE 조건으로
    // 걸러내 GET /routes 목록·탐색 조회에는 뜨지 않는다 -- 다만 GET /routes/{routeId}(직접
    // 조회)와 POST /runs(그 id로 러닝 시작)는 평범한 SketchRoute처럼 그대로 동작한다.
    val isPreviewSegment = bool("is_preview_segment").default(false)

    // 2026-09-26 — 자유 러닝(freeform run)을 "코스로 등록"해 만들어진 행 표시. null이면 종전처럼
    // 운영자가 사전 등록한 큐레이션 코스(com.dallim.route.RouteRepository.createFromRun 참고).
    // v1.3 문서가 전제하는 UGC 모더레이션(금칙어/도로 안전 필터, 완주 전까지 비공개)은 이 라운드
    // 에서 구현하지 않는다 — 사용자 결정(CLAUDE.md 2026-09-26).
    val createdByUserId = varchar("created_by_user_id", 32).references(UserTable.id).nullable()
    // 어느 러닝에서 만들어졌는지 — 중복 등록 방지 체크(RouteRepository.findRouteIdBySourceRunId)에
    // 쓴다. UNIQUE라 같은 러닝을 두 번 코스로 등록할 수 없다.
    val sourceRunId = varchar("source_run_id", 32).references(RunRecordTable.id).nullable().uniqueIndex()

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

data class SketchRoute(
    val id: String,
    val name: String,
    val emoji: String,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val difficulty: String?,
    val status: RouteStatus,
    val finisherCount: Int,
    val trafficLightCount: Int,
    val elevationGainM: Int,
    val repeatSegmentPercent: Int,
    val runability: Double,
    val createdAt: Instant,
    val updatedAt: Instant,
    // Populated by a raw-SQL join against `path` (ST_AsGeoJSON) when a single route is loaded;
    // left null on list-style queries that only need summary fields.
    // See com.dallim.common.GeoJsonLineString.
)

/**
 * User <-> SketchRoute saved bookmark (docs/02-api-spec.md 2장 saved-routes).
 */
object SavedRouteTable : Table("saved_routes") {
    val id = varchar("id", 32)
    val userId = varchar("user_id", 32).references(com.dallim.user.UserTable.id)
    val routeId = varchar("route_id", 32).references(SketchRouteTable.id)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_saved_routes_user_route", userId, routeId)
    }
}

data class SavedRoute(
    val id: String,
    val userId: String,
    val routeId: String,
    val createdAt: Instant,
)
