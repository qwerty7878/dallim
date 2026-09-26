package com.dallim.route

import com.dallim.common.GeoJsonLineString
import kotlinx.serialization.Serializable

// GET /home — docs/02-api-spec.md 3장

@Serializable
data class HomeTodaySketch(
    val routeId: String,
    val name: String,
    val emoji: String,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val thumbnailGeoJson: GeoJsonLineString,
)

/**
 * One route the user has a PARTIAL RunRecord against (most recent attempt per route only) —
 * sourced from com.dallim.run.RunRepository.findContinueRoutes. See HomeService.getHome.
 */
@Serializable
data class HomeContinueRoute(
    val routeId: String,
    val name: String,
    val status: String,
    val lastCoveragePercent: Int,
)

/**
 * One of the user's most recent COMPLETED runs — sourced from
 * com.dallim.run.RunRepository.findRecentCompletedRuns. See HomeService.getHome.
 *
 * Carries the route it was run on (routeId/routeName/emoji/thumbnailGeoJson) so Android can
 * render a real GPS thumbnail here too, not just on todaySketch — every course card must
 * (docs/03-design-system.md §3.2 "모든 코스 카드에 필수").
 */
@Serializable
data class HomeRecentRun(
    val runId: String,
    val distanceKm: Double,
    val completedAt: String,
    val routeId: String,
    val routeName: String,
    val emoji: String,
    val thumbnailGeoJson: GeoJsonLineString,
)

@Serializable
data class HomeResponse(
    // Null only if no curated routes exist at all yet (e.g. seed migration not applied) —
    // SPEC's example always shows an object, but nothing in MVP1 guarantees a non-empty catalog.
    val todaySketch: HomeTodaySketch?,
    val continueRoutes: List<HomeContinueRoute>,
    val recentRuns: List<HomeRecentRun>,
)
