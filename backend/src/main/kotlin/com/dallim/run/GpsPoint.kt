package com.dallim.run

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * Raw GPS points uploaded via POST /runs/{runId}/gps-batch (docs/02-api-spec.md 5장).
 *
 * These are the client's uncompressed points, accepted in multiple partial batches
 * during a run (docs/01-feature-spec.md 1.3 GPS pipeline: Room -> WorkManager batch upload,
 * never real-time streaming). They are the input to the finish-judgement algorithm
 * (Haversine distance, coverage, Fréchet match) — the algorithm output is written back
 * onto RunRecord, and a Douglas-Peucker-simplified version becomes RunRecord.actualPath.
 *
 * Kept as plain lat/lng doubles (not PostGIS geography) since these rows are read
 * sequentially per run, not spatially queried.
 */
object GpsPointTable : Table("gps_points") {
    val id = varchar("id", 32)
    val runId = varchar("run_id", 32).references(RunRecordTable.id)
    val sequence = integer("sequence") // client-side ordering within the run, 0-based
    val lat = double("lat")
    val lng = double("lng")
    val timestamp = timestamp("timestamp")
    val accuracyM = double("accuracy_m").nullable()

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_gps_points_run_id_sequence", false, runId, sequence)
    }
}

data class GpsPoint(
    val id: String,
    val runId: String,
    val sequence: Int,
    val lat: Double,
    val lng: Double,
    val timestamp: Instant,
    val accuracyM: Double?,
    val createdAt: Instant,
)
