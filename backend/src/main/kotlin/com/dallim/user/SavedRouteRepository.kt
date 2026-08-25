package com.dallim.user

import com.dallim.common.IdGenerator
import com.dallim.route.SavedRouteTable
import com.dallim.route.SketchRouteTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Persistence for the User<->SketchRoute bookmark (docs/02-api-spec.md 2장 saved-routes).
 * Pure Exposed DSL throughout — unlike com.dallim.route.RouteRepository, nothing here reads
 * the `path` geography column, so no raw SQL is needed (saved-route summaries only need
 * name/emoji/distanceKm from sketch_routes).
 *
 * SavedRouteTable/SketchRouteTable live in the `route` package (see route/SketchRoute.kt) even
 * though this repository is part of the `user` domain — that's where architect scaffolded them.
 */
class SavedRouteRepository(private val database: Database) {

    fun findRoutesForUser(userId: String, page: Int, size: Int): Pair<List<SavedRouteItem>, Int> =
        transaction(database) {
            val totalCount = SavedRouteTable.selectAll()
                .where { SavedRouteTable.userId eq userId }
                .count()
                .toInt()

            val items = (SavedRouteTable innerJoin SketchRouteTable)
                .selectAll()
                .where { SavedRouteTable.userId eq userId }
                .orderBy(SavedRouteTable.createdAt, SortOrder.DESC)
                .limit(size, offset = (page.toLong() * size))
                .map {
                    SavedRouteItem(
                        routeId = it[SketchRouteTable.id],
                        name = it[SketchRouteTable.name],
                        emoji = it[SketchRouteTable.emoji],
                        distanceKm = it[SketchRouteTable.distanceKm],
                        hasRun = false,
                    )
                }

            items to totalCount
        }

    fun routeExists(routeId: String): Boolean = transaction(database) {
        SketchRouteTable.selectAll().where { SketchRouteTable.id eq routeId }.limit(1).count() > 0
    }

    /** Idempotent: saving an already-saved route is a no-op rather than a unique-index violation. */
    fun save(userId: String, routeId: String) {
        transaction(database) {
            val alreadySaved = SavedRouteTable.selectAll()
                .where { (SavedRouteTable.userId eq userId) and (SavedRouteTable.routeId eq routeId) }
                .limit(1)
                .count() > 0

            if (!alreadySaved) {
                SavedRouteTable.insert {
                    it[SavedRouteTable.id] = IdGenerator.savedRoute()
                    it[SavedRouteTable.userId] = userId
                    it[SavedRouteTable.routeId] = routeId
                }
            }
        }
    }

    /** Idempotent: unsaving a route that isn't saved is a no-op, not an error. */
    fun unsave(userId: String, routeId: String) {
        transaction(database) {
            // deleteWhere's op lambda is `Table.(ISqlExpressionBuilder) -> Op<Boolean>` — `eq` is
            // a member of ISqlExpressionBuilder (the parameter, not the Table receiver), so it
            // must be called through `it` explicitly rather than left bare as in a `.where { }`.
            SavedRouteTable.deleteWhere {
                it.run { (SavedRouteTable.userId eq userId) and (SavedRouteTable.routeId eq routeId) }
            }
        }
    }
}
