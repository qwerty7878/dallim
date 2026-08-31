package com.dallim.dallimbook

import com.dallim.common.GeoJsonLineString
import com.dallim.common.PostGis
import com.dallim.run.RunStatus
import java.time.Instant
import javax.sql.DataSource

/**
 * GET /users/me/runs (docs/02-api-spec.md 6장) persistence.
 *
 * S-40 달림북 그리드는 화면 설명상("완주 GPS 그림 2열 그리드", docs/01-feature-spec.md 1.4)
 * RunStatus.COMPLETED인 RunRecord만 대상이다. PARTIAL/ABORTED/UNDER_REVIEW는 완주가 아니라 DNF이므로
 * 이 화면에 노출되지 않는다 — SPEC이 이 필터를 명시적으로 못박지는 않았지만, 그림이 완성되는 유일한
 * 판정 결과가 COMPLETED이므로 이 해석 외에는 타당하지 않다.
 *
 * `run_records.actual_path`는 PostGIS geography 컬럼이라 Exposed로 표현할 수 없으므로
 * (com.dallim.common.PostGis 참고), thumbnailGeoJson을 함께 조회하는 이 목록/통계 쿼리는 전부 raw
 * JDBC로 작성한다 — com.dallim.run.RunRepository.findFinishers와 동일한 컨벤션.
 */
class DallimbookRepository(private val dataSource: DataSource) {

    data class RunListRow(
        val runId: String,
        val routeName: String,
        val distanceKm: Double,
        val completedAt: Instant,
        val thumbnailGeoJson: GeoJsonLineString,
    )

    /**
     * [statusFilter] non-null and not COMPLETED means the caller asked for a status that can
     * never appear in the dallimbook (see class doc) — short-circuit to an empty page instead of
     * round-tripping to the DB for a query that can only return zero rows.
     */
    fun findCompletedRuns(
        userId: String,
        statusFilter: RunStatus?,
        page: Int,
        size: Int,
    ): Pair<List<RunListRow>, Int> {
        if (statusFilter != null && statusFilter != RunStatus.COMPLETED) return emptyList<RunListRow>() to 0

        val totalCount = countCompletedRuns(userId)
        val items = selectCompletedRuns(userId, page, size)
        return items to totalCount
    }

    private fun countCompletedRuns(userId: String): Int {
        val sql = """
            SELECT COUNT(*) AS cnt
            FROM run_records
            WHERE user_id = ? AND status = 'COMPLETED' AND actual_path IS NOT NULL
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, userId)
                stmt.executeQuery().use { rs -> return if (rs.next()) rs.getInt("cnt") else 0 }
            }
        }
    }

    private fun selectCompletedRuns(userId: String, page: Int, size: Int): List<RunListRow> {
        val sql = """
            SELECT r.id AS run_id, s.name AS route_name, r.distance_km AS distance_km,
                   r.finished_at AS finished_at, ${PostGis.asGeoJsonExpr("r.actual_path")} AS geojson
            FROM run_records r
            JOIN sketch_routes s ON s.id = r.route_id
            WHERE r.user_id = ? AND r.status = 'COMPLETED' AND r.actual_path IS NOT NULL
            ORDER BY r.finished_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, userId)
                stmt.setInt(2, size)
                stmt.setInt(3, page * size)
                stmt.executeQuery().use { rs ->
                    return buildList {
                        while (rs.next()) {
                            add(
                                RunListRow(
                                    runId = rs.getString("run_id"),
                                    routeName = rs.getString("route_name"),
                                    distanceKm = rs.getDouble("distance_km"),
                                    completedAt = rs.getTimestamp("finished_at").toInstant(),
                                    thumbnailGeoJson = GeoJsonLineString.fromJson(rs.getString("geojson")),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Header stat ("총 완주 거리") — sum across every COMPLETED run for this user, independent of
     * the current page/status filter (docs/02-api-spec.md 6장: "페이지네이션과 무관"). Deliberately
     * not restricted to `actual_path IS NOT NULL` like the list query above: a run's distance is
     * still real even on the (currently theoretical) chance its thumbnail geometry is missing.
     */
    fun sumCompletedDistanceKm(userId: String): Double {
        val sql = """
            SELECT COALESCE(SUM(distance_km), 0) AS total
            FROM run_records
            WHERE user_id = ? AND status = 'COMPLETED'
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, userId)
                stmt.executeQuery().use { rs -> return if (rs.next()) rs.getDouble("total") else 0.0 }
            }
        }
    }
}
