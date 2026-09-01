package com.dallim.common

import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.ExposedConnection

/**
 * Exposed does not natively model PostGIS `geography`/`geometry` types
 * (see docs/01-feature-spec.md 2.1.1, 2.4), so every spatial column
 * (SketchRoute.path, RunRecord.actual_path) is created directly in the Flyway
 * migration SQL as `geography(LineString, 4326)` and is intentionally NOT
 * declared as a column on the corresponding Exposed Table object below.
 *
 * backend-dev should read/write those columns with raw SQL through this object,
 * e.g.:
 *   PostGis.exec(this) {
 *       it.prepareStatement(
 *           "UPDATE sketch_routes SET path = ST_GeomFromGeoJSON(?) WHERE id = ?"
 *       )
 *   }
 *
 * Kept here (rather than scattered `exec(...)` calls per module) so every spatial
 * query lives behind one seam and the "why raw SQL" reasoning is documented once.
 */
object PostGis {

    /** ST_AsGeoJSON(...) wrapped in a SELECT fragment — use in raw SELECT queries. */
    fun asGeoJsonExpr(column: String): String = "ST_AsGeoJSON($column)"

    /** ST_GeomFromGeoJSON(?) wrapped in an UPDATE/INSERT fragment — bind the GeoJSON string as the parameter. */
    fun geomFromGeoJsonExpr(): String = "ST_GeomFromGeoJSON(?)"

    /**
     * ST_DWithin(...) radius search fragment (meters), used by GET /routes lat/lng/radiusKm filtering
     * (docs/02-api-spec.md 4장). geography type makes ST_DWithin operate in meters directly.
     */
    fun dWithinExpr(column: String): String = "ST_DWithin($column, ST_MakePoint(?, ?)::geography, ?)"

    /** Escape hatch for raw JDBC access when Exposed's `exec` DSL is insufficient. */
    fun <T> withConnection(transaction: Transaction, block: (ExposedConnection<*>) -> T): T =
        block(transaction.connection)
}
