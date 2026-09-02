package com.dallim.common

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

/**
 * Result of a successful OSRM `/route` or `/match` call — the road-snapped/routed geometry plus
 * its total length. `geometry.coordinates` is already `[lng, lat]` pairs (GeoJSON order), same as
 * OSRM's own `geometries=geojson` output, so it plugs directly into [GeoJsonLineString].
 */
data class OsrmRouteResult(
    val distanceMeters: Double,
    val geometry: GeoJsonLineString,
)

/** Thrown by [OsrmClient.route] whenever OSRM can't produce a route (non-"Ok" code, or the call itself fails). */
class OsrmRoutingException(message: String) : RuntimeException(message)

/**
 * Thin client over a self-hosted OSRM instance running the `foot` (pedestrian) profile — see
 * docs/02-api-spec.md 8장 for why (Naver Directions is car-only). `baseUrl` comes from
 * `dallim.osrm.baseUrl` (application.conf, overridable via `OSRM_BASE_URL`), never hardcoded.
 *
 * Two endpoints only, matching what docs/02-api-spec.md 8장 needs:
 * - [route]: multi-waypoint routing (`/route/v1/foot/...`) for draw-convert & discovery loops.
 * - [match]: map-matching a loose trace onto the road network (`/match/v1/foot/...`) for draw-convert.
 */
open class OsrmClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    companion object {
        /** Flat per-point search radius (meters) passed to OSRM `/match` — see [match] doc comment. */
        private const val MATCH_RADIUS_METERS = 30
    }

    @Serializable
    private data class OsrmGeometry(val coordinates: List<List<Double>>)

    @Serializable
    private data class OsrmRouteEntry(val distance: Double, val geometry: OsrmGeometry)

    @Serializable
    private data class OsrmRouteApiResponse(val code: String, val routes: List<OsrmRouteEntry>? = null)

    @Serializable
    private data class OsrmMatchApiResponse(val code: String, val matchings: List<OsrmRouteEntry>? = null)

    /** `GET /route/v1/foot/{lng1},{lat1};...` — ordered multi-waypoint routing. Throws [OsrmRoutingException] on any failure. */
    open suspend fun route(waypoints: List<LatLng>): OsrmRouteResult {
        val body = try {
            httpClient.get("$baseUrl/route/v1/foot/${waypoints.toCoordsParam()}") {
                parameter("overview", "full")
                parameter("geometries", "geojson")
            }.body<OsrmRouteApiResponse>()
        } catch (e: Exception) {
            throw OsrmRoutingException("OSRM route request failed: ${e.message}")
        }

        val best = body.routes?.firstOrNull()
        if (body.code != "Ok" || best == null) {
            throw OsrmRoutingException("OSRM route call returned code=${body.code}")
        }
        return best.toResult()
    }

    /**
     * `GET /match/v1/foot/{lng1},{lat1};...` — snaps a loose trace to the road network.
     * Returns null on `NoMatch` (not an exception).
     *
     * - `radiuses`: OSRM's default per-point search radius is too tight for our two input
     *   sources (finger-drawn points sampled client-side at a screen-space 8dp minimum — whose
     *   real-world spacing balloons at low zoom — and, later, raw GPS traces). We give every
     *   point a generous flat [MATCH_RADIUS_METERS] radius rather than trying to guess per-point
     *   accuracy.
     * - `gaps=ignore`: without it, OSRM's default `gaps=split` behavior chops a trace with any
     *   low-confidence jump into multiple independent `matchings`, and callers that only look at
     *   the first one silently get a tiny fragment back (see git history for the draw-convert bug
     *   this caused). For "snap a user's rough drawing onto roads," one forced-together path beats
     *   several disconnected ones.
     * - Even so, OSRM may still return multiple `matchings` (e.g. the trace crosses a ferry route
     *   or another genuinely unmatchable gap). Rather than take just the first fragment, we
     *   concatenate every fragment's coordinates in order and sum their distances so the result
     *   reflects the whole trace.
     */
    open suspend fun match(trace: List<LatLng>): OsrmRouteResult? {
        val body = httpClient.get("$baseUrl/match/v1/foot/${trace.toCoordsParam()}") {
            parameter("overview", "full")
            parameter("geometries", "geojson")
            parameter("radiuses", trace.joinToString(";") { MATCH_RADIUS_METERS.toString() })
            parameter("gaps", "ignore")
        }.body<OsrmMatchApiResponse>()

        val matchings = body.matchings
        if (body.code != "Ok" || matchings.isNullOrEmpty()) return null

        return OsrmRouteResult(
            distanceMeters = matchings.sumOf { it.distance },
            geometry = GeoJsonLineString(coordinates = matchings.flatMap { it.geometry.coordinates }),
        )
    }

    private fun List<LatLng>.toCoordsParam(): String = joinToString(";") { "${it.lng},${it.lat}" }

    private fun OsrmRouteEntry.toResult() = OsrmRouteResult(
        distanceMeters = distance,
        geometry = GeoJsonLineString(coordinates = geometry.coordinates),
    )
}
