package com.dallim.run

import com.dallim.route.SketchRouteTable
import io.lettuce.core.api.StatefulRedisConnection
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * Route.finisherCount concurrency handling (docs/01-feature-spec.md 2.2.D: "Route 완주자 수
 * 원자적 증가, Redis INCR 후 배치로 DB 반영 — 동시성 대응").
 *
 * [recordFinisher] is called synchronously from RunService.finishRun on every COMPLETED run — an
 * O(1) Redis INCR, so concurrent finishers never contend on the same Postgres row directly.
 * [flushToDatabase] is invoked periodically by a background coroutine (started in
 * com.dallim.Application, since Ktor has no Spring-style `@Scheduled` — see
 * docs/01-feature-spec.md 2.3) and folds each route's accumulated delta into
 * `sketch_routes.finisher_count` in one UPDATE per dirty route.
 */
class FinisherCountSync(
    private val redis: StatefulRedisConnection<String, String>,
    private val database: Database,
) {
    companion object {
        private const val DIRTY_ROUTES_SET_KEY = "route:finisher:dirty_routes"
        private fun pendingKey(routeId: String) = "route:finisher:pending:$routeId"
    }

    fun recordFinisher(routeId: String) {
        val sync = redis.sync()
        sync.incr(pendingKey(routeId))
        sync.sadd(DIRTY_ROUTES_SET_KEY, routeId)
    }

    /**
     * Each dirty route's pending counter is atomically read-and-reset (GETSET -> "0") so a
     * finisher recorded *during* this flush is never lost; the delta read back is only applied to
     * Postgres (and the route cleared from the dirty set) when it's actually > 0.
     */
    fun flushToDatabase() {
        val sync = redis.sync()
        val routeIds = sync.smembers(DIRTY_ROUTES_SET_KEY)
        for (routeId in routeIds) {
            val delta = sync.getset(pendingKey(routeId), "0")?.toIntOrNull() ?: 0
            if (delta > 0) {
                transaction(database) {
                    SketchRouteTable.update({ SketchRouteTable.id eq routeId }) {
                        it[SketchRouteTable.finisherCount] = SketchRouteTable.finisherCount + delta
                    }
                }
            }
            sync.srem(DIRTY_ROUTES_SET_KEY, routeId)
        }
    }
}
