package com.dallim.user

import com.dallim.common.ErrorCodes
import com.dallim.common.NotFoundException
import com.dallim.route.RouteRepository
import com.dallim.run.RunRepository

/**
 * Saved-route business logic — docs/02-api-spec.md 2장. This round's scope is saved-routes
 * only; profile/nickname-check endpoints are a separate user-domain round.
 *
 * Depends on com.dallim.run.RunRepository to fill in `hasRun` (whether the user has a COMPLETED
 * RunRecord against each saved route) and com.dallim.route.RouteRepository to fill in
 * `thumbnailGeoJson` (docs/03-design-system.md §3.2 "모든 코스 카드에 필수") — same
 * "cross-domain repository access lives at the service layer" convention as
 * com.dallim.route.RouteService/HomeService.
 */
class SavedRouteService(
    private val savedRouteRepository: SavedRouteRepository,
    private val runRepository: RunRepository,
    private val routeRepository: RouteRepository,
) {

    fun listSaved(userId: String, page: Int, size: Int): SavedRoutesResponse {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val (items, totalCount) = savedRouteRepository.findRoutesForUser(userId, safePage, safeSize)

        val routeIds = items.map { it.routeId }
        val completedRouteIds = runRepository.findCompletedRouteIds(userId, routeIds)
        val thumbnails = routeRepository.findThumbnailsByIds(routeIds)
        val enrichedItems = items.map {
            it.copy(
                hasRun = it.routeId in completedRouteIds,
                thumbnailGeoJson = thumbnails[it.routeId] ?: it.thumbnailGeoJson,
            )
        }

        return SavedRoutesResponse(items = enrichedItems, totalCount = totalCount)
    }

    fun save(userId: String, routeId: String) {
        if (!savedRouteRepository.routeExists(routeId)) {
            throw NotFoundException(ErrorCodes.ROUTE_NOT_FOUND, "코스를 찾을 수 없습니다.")
        }
        savedRouteRepository.save(userId, routeId)
    }

    fun unsave(userId: String, routeId: String) {
        savedRouteRepository.unsave(userId, routeId)
    }
}
