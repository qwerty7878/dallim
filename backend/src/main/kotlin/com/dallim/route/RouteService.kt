package com.dallim.route

import com.dallim.common.ErrorCodes
import com.dallim.common.NotFoundException

/**
 * Route domain business logic — docs/02-api-spec.md 4장, docs/01-feature-spec.md 2.2.C.
 * Route handlers (RouteRoutes.kt) stay thin HTTP adapters; all validation/mapping lives here.
 */
class RouteService(private val routeRepository: RouteRepository) {

    fun listRoutes(
        lat: Double?,
        lng: Double?,
        radiusKm: Double,
        minDistanceKm: Double?,
        maxDistanceKm: Double?,
        status: RouteStatus?,
        sort: String,
        page: Int,
        size: Int,
        userId: String?,
    ): RouteListResponse {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)

        val (rows, totalCount) = routeRepository.search(
            lat = lat,
            lng = lng,
            radiusKm = radiusKm,
            minDistanceKm = minDistanceKm,
            maxDistanceKm = maxDistanceKm,
            status = status,
            sort = sort,
            page = safePage,
            size = safeSize,
        )

        // Batch isSaved lookup (single IN-query) instead of one saved_routes lookup per item,
        // to avoid N+1 queries. Optional JWT: unauthenticated requests get isSaved = false.
        val savedRouteIds = if (userId != null) {
            routeRepository.findSavedRouteIds(userId, rows.map { it.id })
        } else {
            emptySet()
        }

        return RouteListResponse(
            items = rows.map { it.toSummary(isSaved = it.id in savedRouteIds) },
            totalCount = totalCount,
            page = safePage,
            size = safeSize,
        )
    }

    fun getDetail(routeId: String, userId: String?): RouteDetailResponse {
        val row = routeRepository.findDetail(routeId)
            ?: throw NotFoundException(ErrorCodes.ROUTE_NOT_FOUND, "코스를 찾을 수 없습니다.")

        val isSaved = userId != null && routeRepository.isSaved(userId, routeId)

        return RouteDetailResponse(
            routeId = row.id,
            name = row.name,
            emoji = row.emoji,
            geoJson = row.geoJson,
            distanceKm = row.distanceKm,
            estimatedMinutes = row.estimatedMinutes,
            difficulty = row.difficulty,
            status = row.status,
            finisherCount = row.finisherCount,
            trafficLightCount = row.trafficLightCount,
            elevationGainM = row.elevationGainM,
            repeatSegmentPercent = row.repeatSegmentPercent,
            runability = row.runability,
            isSaved = isSaved,
            // TODO: docs/02-api-spec.md 4장 shows a `topFeedbackTags` example, but no feedback
            // collection feature/table exists anywhere in SPEC yet — left empty rather than
            // fabricating data. Revisit once a feedback-tagging feature is actually specified.
            topFeedbackTags = emptyList(),
        )
    }

    /**
     * GET /routes/{routeId}/finishers — docs/02-api-spec.md 4장.
     * TODO(backend-dev, run domain round): once RunRecord + finish-judgement lands, populate
     * this from COMPLETED runs against this route (recent N, with userNickname + a simplified
     * thumbnailGeoJson). Until then there is no run data to source from at all, so this always
     * returns an empty list after validating the route itself exists.
     */
    fun getFinishers(routeId: String): RouteFinishersResponse {
        if (!routeRepository.exists(routeId)) {
            throw NotFoundException(ErrorCodes.ROUTE_NOT_FOUND, "코스를 찾을 수 없습니다.")
        }
        return RouteFinishersResponse(items = emptyList())
    }

    /** Used by GET /home todaySketch (see HomeService) — POPULAR-preferred pick, else random. */
    fun pickTodaySketchCandidate(): RouteRepository.RouteRow? = routeRepository.findTodaySketchCandidate()

    private fun RouteRepository.RouteRow.toSummary(isSaved: Boolean) = RouteSummaryResponse(
        routeId = id,
        name = name,
        emoji = emoji,
        distanceKm = distanceKm,
        estimatedMinutes = estimatedMinutes,
        status = status,
        finisherCount = finisherCount,
        thumbnailGeoJson = geoJson,
        isSaved = isSaved,
    )
}
