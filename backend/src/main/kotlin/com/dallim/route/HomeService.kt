package com.dallim.route

import com.dallim.run.RunRepository

/**
 * GET /home — docs/02-api-spec.md 3장. Composed mostly from route data, so it lives in the
 * route package rather than a dedicated "home" module (docs/01-feature-spec.md 2.1 doesn't list
 * one either).
 */
class HomeService(
    private val routeService: RouteService,
    private val runRepository: RunRepository,
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
) {

    /**
     * `lat`/`lng` are accepted per SPEC ("오늘의 달림 추천용") but not yet used to bias the pick —
     * todaySketch currently just prefers a POPULAR route, else a random one, regardless of
     * location. Wiring a real distance-aware pick can reuse RouteRepository.search once needed.
     *
     * continueRoutes: the user's PARTIAL RunRecords, one (most recent) per route. Capped at 5 —
     * SPEC doesn't name a limit for this section (unlike recentRuns, see below), so this is a
     * judgment call rather than a documented number.
     *
     * recentRuns: the user's COMPLETED RunRecords, newest first, capped at 3 — matching
     * docs/01-feature-spec.md §1.2 S-10's explicit "최근 달림 3개".
     */
    fun getHome(lat: Double?, lng: Double?, userId: String): HomeResponse {
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

        val continueRoutes = runRepository.findContinueRoutes(userId, limit = CONTINUE_ROUTES_LIMIT).map {
            HomeContinueRoute(
                routeId = it.routeId,
                name = it.routeName,
                status = it.status.name,
                lastCoveragePercent = it.lastCoveragePercent,
            )
        }

        val recentRuns = runRepository.findRecentCompletedRuns(userId, limit = RECENT_RUNS_LIMIT).map {
            HomeRecentRun(
                runId = it.runId,
                distanceKm = it.distanceKm,
                completedAt = it.completedAt.toString(),
                routeId = it.routeId,
                routeName = it.routeName,
                emoji = it.emoji,
                thumbnailGeoJson = it.thumbnailGeoJson,
            )
        }

        return HomeResponse(
            todaySketch = todaySketch,
            continueRoutes = continueRoutes,
            recentRuns = recentRuns,
            weekSummary = weekSummary(userId, clock.instant()),
        )
    }

    /** 월요일 00:00(Asia/Seoul)부터 지금까지의 완주 기록으로 요일/거리를 집계한다. */
    private fun weekSummary(userId: String, now: java.time.Instant): HomeWeekSummary {
        val today = now.atZone(KST).toLocalDate()
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val rows = runRepository.findCompletedRunsSince(userId, weekStart.atStartOfDay(KST).toInstant())
        return HomeWeekSummary(
            distanceKm = Math.round(rows.sumOf { it.distanceKm } * 100) / 100.0,
            runCount = rows.size,
            runDays = rows.map { it.finishedAt.atZone(KST).dayOfWeek.value }.distinct().sorted(),
            todayDayOfWeek = today.dayOfWeek.value,
        )
    }

    private companion object {
        const val CONTINUE_ROUTES_LIMIT = 5
        const val RECENT_RUNS_LIMIT = 3
        val KST: java.time.ZoneId = java.time.ZoneId.of("Asia/Seoul")
    }
}
