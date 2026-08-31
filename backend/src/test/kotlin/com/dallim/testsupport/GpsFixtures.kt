package com.dallim.testsupport

import com.dallim.common.LatLng
import com.dallim.run.TimedPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Loader for the hand-built GPS scenario fixtures under
 * `src/test/resources/gps-fixtures/` (see RunJudgementServiceTest).
 *
 * Each fixture bundles both the planned route and a realistic actual GPS trace (real
 * Seoul/Gyeonggi-ish coordinates, mirroring rt_001 from V2__seed_curated_routes.sql) so a whole
 * finish-judgement scenario is self-contained and reviewable in one file.
 */
@Serializable
data class FixtureLatLng(val lat: Double, val lng: Double)

@Serializable
data class FixtureGpsPoint(val lat: Double, val lng: Double, val timestamp: String)

@Serializable
data class GpsFixture(
    val description: String,
    val scenario: String,
    val planned: List<FixtureLatLng>,
    val actual: List<FixtureGpsPoint>,
)

object GpsFixtures {
    private val json = Json { ignoreUnknownKeys = true }

    /** Loads `src/test/resources/gps-fixtures/<name>.json` (no extension needed). */
    fun load(name: String): GpsFixture {
        val resourcePath = "/gps-fixtures/$name.json"
        val stream = requireNotNull(GpsFixtures::class.java.getResourceAsStream(resourcePath)) {
            "GPS fixture not found on classpath: $resourcePath"
        }
        return stream.use { json.decodeFromString(it.readBytes().decodeToString()) }
    }
}

fun GpsFixture.plannedLatLngs(): List<LatLng> = planned.map { LatLng(it.lat, it.lng) }

fun GpsFixture.actualTimedPoints(): List<TimedPoint> =
    actual.map { TimedPoint(lat = it.lat, lng = it.lng, timestamp = Instant.parse(it.timestamp)) }
