package com.dallim.route

import com.dallim.common.BadRequestException
import com.dallim.common.ErrorCodes
import com.dallim.common.NotFoundException
import com.dallim.run.RunRepository
import kotlin.math.roundToInt

/**
 * Route domain business logic — docs/02-api-spec.md 4장, docs/01-feature-spec.md 2.2.C.
 * Route handlers (RouteRoutes.kt) stay thin HTTP adapters; all validation/mapping lives here.
 */
class RouteService(
    private val routeRepository: RouteRepository,
    private val runRepository: RunRepository,
) {

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
            // Top-3 most-submitted tags from POST /runs/{runId}/feedback-tags (docs/01-feature-spec.md
            // 2.2.G). Counted in Kotlin rather than a SQL GROUP BY, matching this codebase's existing
            // convention for small aggregate counts (see RunRepository.findFeedbackTagsByRoute).
            topFeedbackTags = runRepository.findFeedbackTagsByRoute(routeId)
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key },
            shapeVotes = tallyShapeVotes(routeId),
            myShapeVote = userId?.let { routeRepository.findMyShapeVote(routeId, it) },
        )
    }

    /**
     * POST /routes/{routeId}/shape-votes -- free-text label, trimmed to 1~10 chars. Upserts
     * (route_id, user_id) so a re-vote replaces rather than accumulates (docs/02-api-spec.md
     * 4장). Anyone authenticated may vote regardless of whether they've run the route.
     */
    fun submitShapeVote(routeId: String, userId: String, rawLabel: String): ShapeVoteSubmitResponse {
        if (!routeRepository.exists(routeId)) {
            throw NotFoundException(ErrorCodes.ROUTE_NOT_FOUND, "코스를 찾을 수 없습니다.")
        }
        val label = rawLabel.trim()
        if (label.isEmpty() || label.length > 10) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "label은 공백 제외 1~10자여야 합니다.")
        }

        routeRepository.upsertShapeVote(routeId, userId, label)
        return ShapeVoteSubmitResponse(shapeVotes = tallyShapeVotes(routeId), myLabel = label)
    }

    /** Top-5 labels by vote count, as rounded percentages of the route's total vote count.
     * Empty when the route has no votes yet. Rounding is per-item against the total, so the sum
     * across items may land slightly off 100 -- acceptable per SPEC ("과설계 금지"). */
    private fun tallyShapeVotes(routeId: String): List<ShapeVoteTally> {
        val labels = routeRepository.findShapeVoteLabels(routeId)
        if (labels.isEmpty()) return emptyList()
        val total = labels.size
        return labels.groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { (label, count) -> ShapeVoteTally(label = label, percent = (count * 100.0 / total).roundToInt()) }
    }

    /** GET /routes/{routeId}/finishers — recent COMPLETED runs against this route (docs/02-api-spec.md 4장). */
    fun getFinishers(routeId: String): RouteFinishersResponse {
        if (!routeRepository.exists(routeId)) {
            throw NotFoundException(ErrorCodes.ROUTE_NOT_FOUND, "코스를 찾을 수 없습니다.")
        }
        val items = runRepository.findFinishers(routeId).map {
            RouteFinisherItem(runId = it.runId, userNickname = it.userNickname, thumbnailGeoJson = it.thumbnailGeoJson)
        }
        return RouteFinishersResponse(items = items)
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
