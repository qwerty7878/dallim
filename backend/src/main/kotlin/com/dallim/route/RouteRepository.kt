package com.dallim.route

import com.dallim.common.GeoJsonLineString
import com.dallim.common.IdGenerator
import com.dallim.common.PostGis
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import javax.sql.DataSource

/**
 * Route persistence — docs/02-api-spec.md 4장, docs/01-feature-spec.md 2.2.C.
 *
 * `sketch_routes.path` is a PostGIS `geography(LineString, 4326)` column that Exposed cannot
 * express (see com.dallim.common.PostGis). Every query that touches it (radius search via
 * ST_DWithin, GeoJSON projection via ST_AsGeoJSON) is therefore raw JDBC against the shared
 * HikariCP [dataSource] rather than the Exposed DSL. Queries that only need saved_routes /
 * sketch_routes' non-spatial columns (isSaved / exists checks) use plain Exposed + [database]
 * instead, matching the rest of the codebase.
 */
class RouteRepository(
    private val dataSource: DataSource,
    private val database: Database,
) {

    data class RouteRow(
        val id: String,
        val name: String,
        val emoji: String,
        val geoJson: GeoJsonLineString,
        val distanceKm: Double,
        val estimatedMinutes: Int,
        val difficulty: String?,
        val status: RouteStatus,
        val finisherCount: Int,
        val trafficLightCount: Int,
        val elevationGainM: Int,
        val repeatSegmentPercent: Int,
        val runability: Double,
    )

    private val summaryColumns = """
        id, name, emoji, ${PostGis.asGeoJsonExpr("path")} AS geojson, distance_km,
        estimated_minutes, difficulty, status, finisher_count, traffic_light_count,
        elevation_gain_m, repeat_segment_percent, runability
    """.trimIndent()

    /**
     * GET /routes — lat/lng+radiusKm (ST_DWithin, meters) + min/maxDistanceKm + status filters,
     * sort=popular|near|new, page/size. lat/lng are both required for radius filtering and for
     * `sort=near`; when either is absent, `near` silently falls back to newest-first rather than
     * erroring (SPEC doesn't define a validation error for this combination).
     */
    fun search(
        lat: Double?,
        lng: Double?,
        radiusKm: Double,
        minDistanceKm: Double?,
        maxDistanceKm: Double?,
        status: RouteStatus?,
        sort: String,
        page: Int,
        size: Int,
    ): Pair<List<RouteRow>, Int> {
        val conditions = mutableListOf<String>()
        val whereArgs = mutableListOf<Any>()

        if (lat != null && lng != null) {
            conditions += PostGis.dWithinExpr("path")
            whereArgs += lng
            whereArgs += lat
            whereArgs += radiusKm * 1000.0
        }
        if (minDistanceKm != null) {
            conditions += "distance_km >= ?"
            whereArgs += minDistanceKm
        }
        if (maxDistanceKm != null) {
            conditions += "distance_km <= ?"
            whereArgs += maxDistanceKm
        }
        if (status != null) {
            conditions += "status = ?"
            whereArgs += status.name
        }
        val whereSql = if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")

        val orderArgs = mutableListOf<Any>()
        val orderSql = when (sort) {
            "near" -> if (lat != null && lng != null) {
                orderArgs += lng
                orderArgs += lat
                " ORDER BY ST_Distance(path, ST_MakePoint(?, ?)::geography) ASC"
            } else {
                " ORDER BY created_at DESC"
            }
            "new" -> " ORDER BY created_at DESC"
            else -> " ORDER BY finisher_count DESC" // "popular" (also the default)
        }

        dataSource.connection.use { conn ->
            val totalCount = conn.prepareStatement("SELECT COUNT(*) FROM sketch_routes$whereSql").use { stmt ->
                bindArgs(stmt, whereArgs)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }

            val listSql = "SELECT $summaryColumns FROM sketch_routes$whereSql$orderSql LIMIT ? OFFSET ?"
            val items = conn.prepareStatement(listSql).use { stmt ->
                bindArgs(stmt, whereArgs + orderArgs + listOf(size, page * size))
                stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toRouteRow()) } }
            }

            return items to totalCount
        }
    }

    /** GET /routes/{routeId}. Null when no such route exists (caller maps to 404 ROUTE_NOT_FOUND). */
    fun findDetail(routeId: String): RouteRow? {
        val sql = "SELECT $summaryColumns FROM sketch_routes WHERE id = ?"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, routeId)
                stmt.executeQuery().use { rs -> return if (rs.next()) rs.toRouteRow() else null }
            }
        }
    }

    /** Lightweight existence check (no geometry fetch) — used for /finishers 404 + saved-route validation. */
    fun exists(routeId: String): Boolean {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT 1 FROM sketch_routes WHERE id = ?").use { stmt ->
                stmt.setString(1, routeId)
                stmt.executeQuery().use { rs -> return rs.next() }
            }
        }
    }

    /**
     * GET /home todaySketch candidate — prefers a POPULAR route, otherwise a random one.
     * Null only when no curated routes exist at all (e.g. seed migration not yet applied).
     */
    fun findTodaySketchCandidate(): RouteRow? {
        val sql = "SELECT $summaryColumns FROM sketch_routes ORDER BY (status = 'POPULAR') DESC, RANDOM() LIMIT 1"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs -> return if (rs.next()) rs.toRouteRow() else null }
            }
        }
    }

    /** GET /routes/{routeId} isSaved — no geometry involved, so plain Exposed against SavedRouteTable. */
    fun isSaved(userId: String, routeId: String): Boolean = transaction(database) {
        SavedRouteTable.selectAll()
            .where { (SavedRouteTable.userId eq userId) and (SavedRouteTable.routeId eq routeId) }
            .limit(1)
            .count() > 0
    }

    /**
     * GET /routes list isSaved — batch lookup of which of [routeIds] are saved by [userId], in a
     * single `routeId IN (...) AND userId = ?` query instead of one round-trip per item (N+1).
     * Returns the subset of [routeIds] that are saved; empty when [routeIds] is empty.
     */
    fun findSavedRouteIds(userId: String, routeIds: List<String>): Set<String> {
        if (routeIds.isEmpty()) return emptySet()
        return transaction(database) {
            SavedRouteTable.select(SavedRouteTable.routeId)
                .where { (SavedRouteTable.userId eq userId) and (SavedRouteTable.routeId inList routeIds) }
                .mapTo(mutableSetOf()) { it[SavedRouteTable.routeId] }
        }
    }

    // --- Shape votes (route_shape_votes has no geometry column -> plain Exposed) ---

    /**
     * POST /routes/{routeId}/shape-votes -- upsert keyed by (route_id, user_id): a re-vote
     * replaces the existing label rather than accumulating a second row (same
     * exists-then-update-or-insert style as com.dallim.push.DeviceTokenRepository.upsert).
     */
    fun upsertShapeVote(routeId: String, userId: String, label: String) {
        transaction(database) {
            val exists = RouteShapeVoteTable.selectAll()
                .where { (RouteShapeVoteTable.routeId eq routeId) and (RouteShapeVoteTable.userId eq userId) }
                .limit(1)
                .count() > 0

            if (exists) {
                RouteShapeVoteTable.update({ (RouteShapeVoteTable.routeId eq routeId) and (RouteShapeVoteTable.userId eq userId) }) {
                    it[RouteShapeVoteTable.label] = label
                    it[RouteShapeVoteTable.updatedAt] = Instant.now()
                }
            } else {
                RouteShapeVoteTable.insert {
                    it[RouteShapeVoteTable.id] = IdGenerator.shapeVote()
                    it[RouteShapeVoteTable.routeId] = routeId
                    it[RouteShapeVoteTable.userId] = userId
                    it[RouteShapeVoteTable.label] = label
                }
            }
        }
    }

    /** Every label ever submitted for [routeId], for the caller to tally (see
     * RouteService.tallyShapeVotes). No SQL-side GROUP BY/COUNT -- same convention as
     * findFeedbackTagsByRoute above. */
    fun findShapeVoteLabels(routeId: String): List<String> = transaction(database) {
        RouteShapeVoteTable.select(RouteShapeVoteTable.label)
            .where { RouteShapeVoteTable.routeId eq routeId }
            .map { it[RouteShapeVoteTable.label] }
    }

    /** GET /routes/{routeId} `myShapeVote` -- null when [userId] is null or hasn't voted. */
    fun findMyShapeVote(routeId: String, userId: String): String? = transaction(database) {
        RouteShapeVoteTable.select(RouteShapeVoteTable.label)
            .where { (RouteShapeVoteTable.routeId eq routeId) and (RouteShapeVoteTable.userId eq userId) }
            .limit(1)
            .map { it[RouteShapeVoteTable.label] }
            .firstOrNull()
    }

    private fun bindArgs(stmt: PreparedStatement, args: List<Any>) {
        args.forEachIndexed { index, value ->
            when (value) {
                is String -> stmt.setString(index + 1, value)
                is Double -> stmt.setDouble(index + 1, value)
                is Int -> stmt.setInt(index + 1, value)
                else -> stmt.setObject(index + 1, value)
            }
        }
    }

    private fun ResultSet.toRouteRow() = RouteRow(
        id = getString("id"),
        name = getString("name"),
        emoji = getString("emoji"),
        geoJson = GeoJsonLineString.fromJson(getString("geojson")),
        distanceKm = getDouble("distance_km"),
        estimatedMinutes = getInt("estimated_minutes"),
        difficulty = getString("difficulty"),
        status = RouteStatus.valueOf(getString("status")),
        finisherCount = getInt("finisher_count"),
        trafficLightCount = getInt("traffic_light_count"),
        elevationGainM = getInt("elevation_gain_m"),
        repeatSegmentPercent = getInt("repeat_segment_percent"),
        runability = getDouble("runability"),
    )
}
