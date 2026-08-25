package com.dallim.user

import com.dallim.common.ErrorCodes
import com.dallim.common.NotFoundException

/**
 * Saved-route business logic — docs/02-api-spec.md 2장. This round's scope is saved-routes
 * only; profile/nickname-check endpoints are a separate user-domain round.
 */
class SavedRouteService(private val savedRouteRepository: SavedRouteRepository) {

    fun listSaved(userId: String, page: Int, size: Int): SavedRoutesResponse {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val (items, totalCount) = savedRouteRepository.findRoutesForUser(userId, safePage, safeSize)
        return SavedRoutesResponse(items = items, totalCount = totalCount)
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
