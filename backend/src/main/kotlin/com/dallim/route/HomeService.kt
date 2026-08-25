package com.dallim.route

/**
 * GET /home — docs/02-api-spec.md 3장. Composed mostly from route data, so it lives in the
 * route package rather than a dedicated "home" module (docs/01-feature-spec.md 2.1 doesn't list
 * one either).
 */
class HomeService(private val routeService: RouteService) {

    /**
     * `lat`/`lng` are accepted per SPEC ("오늘의 달림 추천용") but not yet used to bias the pick —
     * todaySketch currently just prefers a POPULAR route, else a random one, regardless of
     * location. Wiring a real distance-aware pick can reuse RouteRepository.search once needed.
     *
     * continueRoutes/recentRuns are TODO(backend-dev, run domain round): both require RunRecord
     * (in-progress/PARTIAL runs, completed run history) which does not exist yet this round —
     * always empty until then.
     */
    @Suppress("UNUSED_PARAMETER")
    fun getHome(lat: Double?, lng: Double?): HomeResponse {
        val candidate = routeService.pickTodaySketchCandidate()
        val todaySketch = candidate?.let {
            HomeTodaySketch(
                routeId = it.id,
                name = it.name,
                emoji = it.emoji,
                distanceKm = it.distanceKm,
                estimatedMinutes = it.estimatedMinutes,
                thumbnailGeoJson = it.geoJson,
            )
        }

        return HomeResponse(
            todaySketch = todaySketch,
            continueRoutes = emptyList(),
            recentRuns = emptyList(),
        )
    }
}
