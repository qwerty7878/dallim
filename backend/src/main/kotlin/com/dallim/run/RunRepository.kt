package com.dallim.run

import com.dallim.common.GeoJsonLineString
import com.dallim.common.IdGenerator
import com.dallim.common.LatLng
import com.dallim.common.PostGis
import com.dallim.route.SketchRouteTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import javax.sql.DataSource

/**
 * RunRecord/GpsPoint persistence — docs/02-api-spec.md 5장, docs/01-feature-spec.md 2.2.D.
 *
 * `run_records.actual_path` and `sketch_routes.path` are PostGIS `geography(LineString, 4326)`
 * columns Exposed cannot express (see com.dallim.common.PostGis), so every query touching them
 * is raw JDBC against [dataSource], matching com.dallim.route.RouteRepository's convention.
 * Everything else (run_records' scalar columns, gps_points) uses plain Exposed DSL against
 * [database].
 */
class RunRepository(
    private val dataSource: DataSource,
    private val database: Database,
) {

    data class RunRow(
        val id: String,
        val userId: String,
        val routeId: String?,
        val status: RunStatus,
        val startedAt: Instant,
        val finishedAt: Instant?,
        val distanceKm: Double?,
        val durationSeconds: Int?,
        val averagePaceSecPerKm: Int?,
        val sketchMatchPercent: Int?,
        val routeCompletionPercent: Int?,
        val stepCount: Int?,
    )

    data class FinisherRow(
        val runId: String,
        val userNickname: String,
        val thumbnailGeoJson: GeoJsonLineString,
    )

    /** GET /home `continueRoutes` — one row per route the user has an unfinished (PARTIAL) attempt at. */
    data class ContinueRouteRow(
        val routeId: String,
        val routeName: String,
        val status: RunStatus,
        val lastCoveragePercent: Int,
    )

    /**
     * GET /home `recentRuns` — one row per COMPLETED run, joined back to the sketch route it was
     * run on so the home screen can render a real GPS thumbnail instead of a bare text card
     * (docs/03-design-system.md §3.2 "모든 코스 카드에 필수").
     */
    data class RecentRunRow(
        val runId: String,
        val distanceKm: Double,
        val completedAt: Instant,
        /** null이면 자유 러닝 — 이때 [routeName]은 "자유 러닝", [emoji]는 null, 썸네일은 실제 궤적. */
        val routeId: String?,
        val routeName: String,
        val emoji: String?,
        val thumbnailGeoJson: GeoJsonLineString,
    )

    /** 이번 주 요약용 — 완주(COMPLETED) 기록의 종료 시각과 거리만. */
    data class WeekRunRow(val finishedAt: Instant, val distanceKm: Double)

    /**
     * POST /runs — creates the session row (IN_PROGRESS) and returns its generated id. [routeId]
     * is null for a freeform run (2026-09-26, 사용자 요청 — 코스 미선택 자유 러닝).
     */
    fun create(userId: String, routeId: String?, mode: String, startedAt: Instant): String {
        val id = IdGenerator.run()
        transaction(database) {
            RunRecordTable.insert {
                it[RunRecordTable.id] = id
                it[RunRecordTable.userId] = userId
                it[RunRecordTable.routeId] = routeId
                it[RunRecordTable.mode] = mode
                it[RunRecordTable.status] = RunStatus.IN_PROGRESS
                it[RunRecordTable.startedAt] = startedAt
            }
        }
        return id
    }

    fun findById(runId: String): RunRow? = transaction(database) {
        RunRecordTable.selectAll()
            .where { RunRecordTable.id eq runId }
            .singleOrNull()
            ?.toRunRow()
    }

    /** PATCH /runs/{runId}/status — RUNNING/PAUSED only; finish-judgement statuses go through [saveJudgement]. */
    fun updateStatus(runId: String, status: RunStatus) {
        transaction(database) {
            RunRecordTable.update({ RunRecordTable.id eq runId }) {
                it[RunRecordTable.status] = status
                it[RunRecordTable.updatedAt] = Instant.now()
            }
        }
    }

    /** GET /runs/{runId} routeName — non-spatial, so plain Exposed against sketch_routes. */
    fun findRouteName(routeId: String): String? = transaction(database) {
        SketchRouteTable.select(SketchRouteTable.name)
            .where { SketchRouteTable.id eq routeId }
            .singleOrNull()
            ?.get(SketchRouteTable.name)
    }

    /** isFirstDiscoverer: whether any *other* run against this route already finished COMPLETED. */
    fun hasPriorCompletedRun(routeId: String, excludingRunId: String): Boolean = transaction(database) {
        RunRecordTable.selectAll()
            .where {
                (RunRecordTable.routeId eq routeId) and
                    (RunRecordTable.status eq RunStatus.COMPLETED) and
                    (RunRecordTable.id neq excludingRunId)
            }
            .limit(1)
            .count() > 0
    }

    /**
     * GET /home `continueRoutes` — the user's most recent PARTIAL run per route, newest first
     * (a route the user has stopped and restarted PARTIAL several times must only surface its
     * latest attempt). Non-spatial (only run_records + sketch_routes.name), so plain Exposed.
     */
    fun findContinueRoutes(userId: String, limit: Int): List<ContinueRouteRow> = transaction(database) {
        // Explicit `on` — as of 2026-09-26 there are two FK paths between these tables
        // (run_records.route_id -> sketch_routes.id, and sketch_routes.source_run_id ->
        // run_records.id for "코스로 등록"), so Exposed's parameterless innerJoin can no longer
        // auto-detect a single unambiguous join column.
        RunRecordTable.join(SketchRouteTable, JoinType.INNER, onColumn = RunRecordTable.routeId, otherColumn = SketchRouteTable.id)
            .selectAll()
            .where { (RunRecordTable.userId eq userId) and (RunRecordTable.status eq RunStatus.PARTIAL) }
            .orderBy(RunRecordTable.finishedAt, SortOrder.DESC)
            .map {
                ContinueRouteRow(
                    // innerJoin against SketchRouteTable guarantees a matched routeId, so a
                    // freeform run's null route_id (2026-09-26) can never appear in this result set.
                    routeId = it[RunRecordTable.routeId]!!,
                    routeName = it[SketchRouteTable.name],
                    status = it[RunRecordTable.status],
                    lastCoveragePercent = it[RunRecordTable.routeCompletionPercent] ?: 0,
                )
            }
            .distinctBy { it.routeId } // rows already ordered newest-first, so first occurrence wins
            .take(limit)
    }

    /**
     * GET /home `recentRuns` — the user's most recently finished COMPLETED runs, joined to
     * sketch_routes for the thumbnail. Touches the `path` geometry column, so raw JDBC (like
     * [findFinishers]) rather than the Exposed DSL used by the rest of this class's non-spatial
     * queries.
     */
    fun findRecentCompletedRuns(userId: String, limit: Int): List<RecentRunRow> {
        val sql = """
            SELECT r.id AS run_id, r.distance_km, r.finished_at, r.route_id,
                   COALESCE(sr.name, '자유 러닝') AS route_name, sr.emoji AS emoji,
                   ${PostGis.asGeoJsonExpr("COALESCE(sr.path, r.actual_path)")} AS geojson
            FROM run_records r
            LEFT JOIN sketch_routes sr ON sr.id = r.route_id
            WHERE r.user_id = ? AND r.status = 'COMPLETED' AND COALESCE(sr.path, r.actual_path) IS NOT NULL
            ORDER BY r.finished_at DESC
            LIMIT ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, userId)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    return buildList {
                        while (rs.next()) {
                            add(
                                RecentRunRow(
                                    runId = rs.getString("run_id"),
                                    distanceKm = rs.getDouble("distance_km"),
                                    completedAt = rs.getTimestamp("finished_at").toInstant(),
                                    routeId = rs.getString("route_id"),
                                    routeName = rs.getString("route_name"),
                                    emoji = rs.getString("emoji"),
                                    thumbnailGeoJson = GeoJsonLineString.fromJson(rs.getString("geojson")),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    /** [since] 이후 끝난 완주 기록 전부(주간 요약 계산용, 한 주치라 건수가 작다). */
    fun findCompletedRunsSince(userId: String, since: Instant): List<WeekRunRow> {
        val sql = """
            SELECT finished_at, distance_km
            FROM run_records
            WHERE user_id = ? AND status = 'COMPLETED' AND finished_at >= ? AND distance_km IS NOT NULL
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, userId)
                stmt.setTimestamp(2, java.sql.Timestamp.from(since))
                stmt.executeQuery().use { rs ->
                    return buildList {
                        while (rs.next()) {
                            add(WeekRunRow(rs.getTimestamp("finished_at").toInstant(), rs.getDouble("distance_km")))
                        }
                    }
                }
            }
        }
    }

    /**
     * GET /users/me/saved-routes `hasRun` — of [routeIds], which ones [userId] has at least one
     * COMPLETED RunRecord against. See com.dallim.user.SavedRouteService.
     */
    fun findCompletedRouteIds(userId: String, routeIds: Collection<String>): Set<String> {
        if (routeIds.isEmpty()) return emptySet()
        return transaction(database) {
            RunRecordTable.select(RunRecordTable.routeId)
                .where {
                    (RunRecordTable.userId eq userId) and
                        (RunRecordTable.status eq RunStatus.COMPLETED) and
                        (RunRecordTable.routeId inList routeIds)
                }
                // routeId inList routeIds (non-null strings) can never match a freeform run's
                // null route_id (2026-09-26), so this is never actually null here.
                .map { it[RunRecordTable.routeId]!! }
                .toSet()
        }
    }

    // --- GPS points (gps_points has no geometry column -> plain Exposed) ---

    /**
     * POST /runs/{runId}/gps-batch — appends [points] after any already-stored points for this
     * run (CLAUDE.md rule 4: batches can arrive partially/multiple times) and returns the run's
     * total stored point count so far (the `receivedCount` in the response is cumulative, per the
     * docs/02-api-spec.md 5장 example).
     */
    fun appendGpsPoints(runId: String, points: List<GpsPointRequest>): Int = transaction(database) {
        val nextSequenceStart = (
            GpsPointTable.select(GpsPointTable.sequence)
                .where { GpsPointTable.runId eq runId }
                .orderBy(GpsPointTable.sequence, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.get(GpsPointTable.sequence)
                ?: -1
            ) + 1

        points.forEachIndexed { offset, point ->
            GpsPointTable.insert {
                it[GpsPointTable.id] = IdGenerator.gpsPoint()
                it[GpsPointTable.runId] = runId
                it[GpsPointTable.sequence] = nextSequenceStart + offset
                it[GpsPointTable.lat] = point.lat
                it[GpsPointTable.lng] = point.lng
                it[GpsPointTable.timestamp] = Instant.parse(point.timestamp)
                it[GpsPointTable.accuracyM] = point.accuracyM
            }
        }

        GpsPointTable.selectAll().where { GpsPointTable.runId eq runId }.count().toInt()
    }

    fun countGpsPoints(runId: String): Int = transaction(database) {
        GpsPointTable.selectAll().where { GpsPointTable.runId eq runId }.count().toInt()
    }

    /** All raw fixes for a run, in upload order, as judgement input (see [TimedPoint]). */
    fun fetchGpsPoints(runId: String): List<TimedPoint> = transaction(database) {
        GpsPointTable.selectAll()
            .where { GpsPointTable.runId eq runId }
            .orderBy(GpsPointTable.sequence, SortOrder.ASC)
            .map {
                TimedPoint(
                    lat = it[GpsPointTable.lat],
                    lng = it[GpsPointTable.lng],
                    timestamp = it[GpsPointTable.timestamp],
                )
            }
    }

    // --- Geometry (raw SQL -> com.dallim.common.PostGis) ---

    /** The route's planned LineString as judgement input (lat/lng order, not GeoJSON's lng/lat). */
    fun findPlannedPath(routeId: String): List<LatLng>? = findPlannedGeoJson(routeId)?.toLatLngList()

    fun findPlannedGeoJson(routeId: String): GeoJsonLineString? {
        val sql = "SELECT ${PostGis.asGeoJsonExpr("path")} AS geojson FROM sketch_routes WHERE id = ?"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, routeId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    return GeoJsonLineString.fromJson(rs.getString("geojson"))
                }
            }
        }
    }

    /** Null until finish-judgement has run (actual_path is only written by [saveJudgement]). */
    fun findActualGeoJson(runId: String): GeoJsonLineString? {
        val sql = "SELECT ${PostGis.asGeoJsonExpr("actual_path")} AS geojson FROM run_records WHERE id = ?"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, runId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    val geoJson = rs.getString("geojson") ?: return null
                    return GeoJsonLineString.fromJson(geoJson)
                }
            }
        }
    }

    /**
     * Persists the finish-judgement outcome. Non-spatial columns go through Exposed;
     * `actual_path` goes through raw JDBC (ST_GeomFromGeoJSON) on a separate connection, matching
     * com.dallim.route.RouteRepository's convention of never mixing Exposed's DSL with the
     * geography columns it can't express.
     */
    fun saveJudgement(
        runId: String,
        finishedAt: Instant,
        result: RunJudgementResult,
        averagePaceSecPerKm: Int,
        isFirstDiscoverer: Boolean,
        earnedInk: Int,
        stepCount: Int? = null,
    ) {
        transaction(database) {
            RunRecordTable.update({ RunRecordTable.id eq runId }) {
                it[RunRecordTable.status] = result.status
                it[RunRecordTable.finishedAt] = finishedAt
                it[RunRecordTable.distanceKm] = result.distanceMeters / 1000.0
                it[RunRecordTable.durationSeconds] = result.durationSeconds
                it[RunRecordTable.averagePaceSecPerKm] = averagePaceSecPerKm
                it[RunRecordTable.sketchMatchPercent] = result.sketchMatchPercent
                it[RunRecordTable.routeCompletionPercent] = result.routeCompletionPercent
                it[RunRecordTable.isFirstDiscoverer] = isFirstDiscoverer
                it[RunRecordTable.earnedInk] = earnedInk
                it[RunRecordTable.stepCount] = stepCount
                it[RunRecordTable.updatedAt] = Instant.now()
            }
        }

        if (result.simplifiedActualPath.size >= 2) {
            val coordinates = result.simplifiedActualPath.joinToString(prefix = "[", postfix = "]") {
                "[${it.lng},${it.lat}]"
            }
            val geoJson = """{"type":"LineString","coordinates":$coordinates}"""
            val sql = "UPDATE run_records SET actual_path = ${PostGis.geomFromGeoJsonExpr()} WHERE id = ?"
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, geoJson)
                    stmt.setString(2, runId)
                    stmt.executeUpdate()
                }
            }
        }
    }

    /** GET /routes/{routeId}/finishers — recent COMPLETED runs against this route. */
    fun findFinishers(routeId: String, limit: Int = 20): List<FinisherRow> {
        val sql = """
            SELECT r.id AS run_id, u.nickname AS nickname, ${PostGis.asGeoJsonExpr("r.actual_path")} AS geojson
            FROM run_records r
            JOIN users u ON u.id = r.user_id
            WHERE r.route_id = ? AND r.status = 'COMPLETED' AND r.actual_path IS NOT NULL
            ORDER BY r.finished_at DESC
            LIMIT ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, routeId)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    return buildList {
                        while (rs.next()) {
                            add(
                                FinisherRow(
                                    runId = rs.getString("run_id"),
                                    userNickname = rs.getString("nickname") ?: "달림이",
                                    thumbnailGeoJson = GeoJsonLineString.fromJson(rs.getString("geojson")),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Route feedback tags (route_feedback_tags has no geometry column -> plain Exposed) ---

    /**
     * POST /runs/{runId}/feedback-tags. Idempotent at the *run* level, not per-tag: if this run
     * already has any stored tags (from an earlier submission), [tags] is ignored entirely and
     * the existing set is returned unchanged — a resubmission (even with a different selection)
     * is a pure no-op, matching the "3초 컷" low-friction UX (no edit/overwrite flow needed).
     * Returns the run's final stored tag set either way.
     */
    fun submitFeedbackTagsIfAbsent(runId: String, routeId: String, userId: String, tags: List<String>): List<String> =
        transaction(database) {
            val existing = RouteFeedbackTagTable.select(RouteFeedbackTagTable.tag)
                .where { RouteFeedbackTagTable.runId eq runId }
                .map { it[RouteFeedbackTagTable.tag] }
            if (existing.isNotEmpty()) return@transaction existing

            tags.forEach { tag ->
                RouteFeedbackTagTable.insert {
                    it[RouteFeedbackTagTable.id] = IdGenerator.feedbackTag()
                    it[RouteFeedbackTagTable.runId] = runId
                    it[RouteFeedbackTagTable.routeId] = routeId
                    it[RouteFeedbackTagTable.userId] = userId
                    it[RouteFeedbackTagTable.tag] = tag
                }
            }
            tags
        }

    /** GET /routes/{routeId} `topFeedbackTags` — every tag ever submitted for this route, for the
     * caller to rank (see RouteService.getDetail). No SQL-side GROUP BY/COUNT: matches this
     * codebase's existing convention (e.g. MeetupRepository.countParticipantsByMeetup) of doing
     * the count in Kotlin via groupingBy/eachCount rather than a DB aggregate, which is simpler
     * and plenty fast at this table's expected size. */
    fun findFeedbackTagsByRoute(routeId: String): List<String> = transaction(database) {
        RouteFeedbackTagTable.select(RouteFeedbackTagTable.tag)
            .where { RouteFeedbackTagTable.routeId eq routeId }
            .map { it[RouteFeedbackTagTable.tag] }
    }

    private fun ResultRow.toRunRow() = RunRow(
        id = this[RunRecordTable.id],
        userId = this[RunRecordTable.userId],
        routeId = this[RunRecordTable.routeId],
        status = this[RunRecordTable.status],
        startedAt = this[RunRecordTable.startedAt],
        finishedAt = this[RunRecordTable.finishedAt],
        distanceKm = this[RunRecordTable.distanceKm],
        durationSeconds = this[RunRecordTable.durationSeconds],
        averagePaceSecPerKm = this[RunRecordTable.averagePaceSecPerKm],
        sketchMatchPercent = this[RunRecordTable.sketchMatchPercent],
        routeCompletionPercent = this[RunRecordTable.routeCompletionPercent],
        stepCount = this[RunRecordTable.stepCount],
    )
}
