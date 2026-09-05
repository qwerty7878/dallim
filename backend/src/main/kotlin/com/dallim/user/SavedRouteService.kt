package com.dallim.user

import com.dallim.common.ErrorCodes
import com.dallim.common.NotFoundException
import com.dallim.run.RunRepository

/**
 * Saved-route business logic — docs/02-api-spec.md 2장. This round's scope is saved-routes
 * only; profile/nickname-check endpoints are a separate user-domain round.
 *
 * Depends on com.dallim.run.RunRepository only to fill in `hasRun` (whether the user has a
 * COMPLETED RunRecord against each saved route) — same "cross-domain RunRepository access lives
 * at the service layer" convention as com.dallim.route.RouteService/HomeService.
 */
class SavedRouteService(
    private val savedRouteRepository: SavedRouteRepository,
    private val runRepository: RunRepository,
) {

    fun listSaved(userId: String, page: Int, size: Int): SavedRoutesResponse {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val (items, totalCount) = savedRouteRepository.findRoutesForUser(userId, safePage, safeSize)

        val completedRouteIds = runRepository.findCompletedRouteIds(userId, items.map { it.routeId })
        val itemsWithHasRun = items.map { it.copy(hasRun = it.routeId in completedRouteIds) }

        return SavedRoutesResponse(items = itemsWithHasRun, totalCount = totalCount)
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
