package com.dallim.common

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/** One normalized place result from Kakao's keyword search — docs/02-api-spec.md 12.1's `items[]`. */
data class KakaoPlace(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
)

/**
 * Thin client over Kakao Local's keyword search API
 * (`GET https://dapi.kakao.com/v2/local/search/keyword.json`) — docs/02-api-spec.md 12장, used by
 * `GET /routes/places/search` to let S-45 search for a required waypoint by name instead of only
 * a map long-press. Server-side only: `apiKey` (`dallim.kakao.localApiKey` /
 * `KAKAO_LOCAL_REST_API_KEY`) is never sent to clients, same treatment as 9.2's FCM service
 * account key.
 *
 * Kakao returns `x`/`y` as WGS84 longitude/latitude strings directly (unlike Naver's TM128, which
 * would need a coordinate-system conversion), so they map straight onto [LatLng] with no transform
 * — see docs/02-api-spec.md 12장 prose for why Kakao was picked over Naver for this.
 *
 * Soft-fails on purpose (12.1): any upstream error/timeout/non-2xx, or a blank/unset [apiKey]
 * (local dev without the key configured, mirrors com.dallim.push.FirebaseFcmSender's missing-file
 * handling), returns an empty list rather than throwing — place search is a supporting input to
 * the map long-press, not a required path, so a Kakao outage or a missing local key must never
 * fail the request.
 */
open class KakaoLocalClient(
    private val httpClient: HttpClient,
    private val apiKey: String?,
) {
    companion object {
        private const val SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"

        // docs/02-api-spec.md 12.1 — top 5 only, no pagination; this is a long-press substitute,
        // not a full search experience.
        private const val RESULT_SIZE = 5
    }

    private val logger = LoggerFactory.getLogger(KakaoLocalClient::class.java)

    @Serializable
    private data class KakaoDocument(
        @SerialName("place_name") val placeName: String,
        @SerialName("address_name") val addressName: String,
        @SerialName("road_address_name") val roadAddressName: String? = null,
        // Kakao sends these as decimal strings, not JSON numbers.
        val x: String,
        val y: String,
    )

    @Serializable
    private data class KakaoKeywordSearchResponse(val documents: List<KakaoDocument> = emptyList())

    /**
     * `query` must already be validated non-blank by the caller (PlaceSearchService) — this
     * client only handles the upstream call and its own failure modes. `lat`/`lng`, when both
     * given, are forwarded as Kakao's `y`/`x` location-bias hint (docs/02-api-spec.md 12.1).
     */
    open suspend fun searchKeyword(query: String, lat: Double?, lng: Double?): List<KakaoPlace> {
        if (apiKey.isNullOrBlank()) {
            logger.debug("Kakao Local search skipped: KAKAO_LOCAL_REST_API_KEY not configured")
            return emptyList()
        }

        val response = try {
            httpClient.get(SEARCH_URL) {
                header("Authorization", "KakaoAK $apiKey")
                parameter("query", query)
                parameter("size", RESULT_SIZE)
                if (lat != null && lng != null) {
                    parameter("x", lng)
                    parameter("y", lat)
                }
            }
        } catch (e: Exception) {
            logger.warn("Kakao Local search failed: {}", e.message)
            return emptyList()
        }

        if (!response.status.isSuccess()) {
            logger.warn("Kakao Local search returned {}", response.status)
            return emptyList()
        }

        val body = try {
            response.body<KakaoKeywordSearchResponse>()
        } catch (e: Exception) {
            logger.warn("Kakao Local search response parsing failed: {}", e.message)
            return emptyList()
        }

        return body.documents.mapNotNull { it.toKakaoPlaceOrNull() }
    }

    private fun KakaoDocument.toKakaoPlaceOrNull(): KakaoPlace? {
        val lngVal = x.toDoubleOrNull() ?: return null
        val latVal = y.toDoubleOrNull() ?: return null
        return KakaoPlace(
            name = placeName,
            address = roadAddressName?.takeIf { it.isNotBlank() } ?: addressName,
            lat = latVal,
            lng = lngVal,
        )
    }
}
