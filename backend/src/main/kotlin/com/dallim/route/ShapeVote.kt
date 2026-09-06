package com.dallim.route

import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * route_shape_votes — "커뮤니티 투표(모양 맞추기)", docs/02-api-spec.md 4장
 * (POST /routes/{routeId}/shape-votes), docs/01-feature-spec.md 2.2.C,
 * docs/달림_화면별_상세기획서_v1.3.md 297행/271행.
 *
 * [SketchRouteTable.name] is the fixed official name assigned at route creation; this table is a
 * SEPARATE free-text aggregate of "what other runners think this looks like" — labels need not
 * match `name`, and the v1.3 문서 예시 자체가 다수 의견이 공식 이름과 다를 수 있음을 전제로 한다.
 * No fixed candidate vocabulary (unlike RouteFeedbackTags): any 1~10 char trimmed label is
 * accepted and auto-aggregated by exact string match (see RouteService.submitShapeVote).
 *
 * `(route_id, user_id)` UNIQUE — one user keeps at most one live vote per route; a re-vote
 * upserts (replaces) rather than accumulating, see RouteRepository.upsertShapeVote (same
 * exists-then-update-or-insert style as com.dallim.push.DeviceTokenRepository.upsert).
 */
object RouteShapeVoteTable : Table("route_shape_votes") {
    val id = varchar("id", 32)
    val routeId = varchar("route_id", 32).references(SketchRouteTable.id)
    val userId = varchar("user_id", 32).references(UserTable.id)
    val label = varchar("label", 10)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}
