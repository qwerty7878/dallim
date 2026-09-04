package com.dallim.route

import com.dallim.common.BadRequestException
import com.dallim.common.ErrorCodes
import com.dallim.common.KakaoLocalClient

/**
 * GET /routes/places/search (docs/02-api-spec.md 12장) — lets S-45 search for a required
 * waypoint by name (e.g. "안양역") instead of only a map long-press. All actual Kakao-calling
 * logic (including the soft-fail-to-empty-list policy) lives in KakaoLocalClient; this class only
 * owns the one piece of request validation the spec calls out (blank `query` -> 400) and shapes
 * the response.
 */
class PlaceSearchService(
    private val kakaoLocalClient: KakaoLocalClient,
) {
    suspend fun search(query: String?, lat: Double?, lng: Double?): PlaceSearchResponse {
        val trimmed = query?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "검색어를 입력해주세요.")
        }

        val places = kakaoLocalClient.searchKeyword(trimmed, lat, lng)
        return PlaceSearchResponse(
            items = places.map { PlaceSearchItem(name = it.name, address = it.address, lat = it.lat, lng = it.lng) },
        )
    }
}
