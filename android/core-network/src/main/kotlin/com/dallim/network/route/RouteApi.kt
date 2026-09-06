package com.dallim.network.route

import com.dallim.network.common.ApiResponse
import com.dallim.network.common.GeoJsonLineString
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * docs/02-api-spec.md 4장 — S-11 탐색 / S-16 Route 상세.
 * docs/02-api-spec.md 8장 — S-43~S-45 코스 생성(직접 그리기/AI 자동 생성).
 */
interface RouteApi {
    @GET("routes")
    suspend fun getRoutes(
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radiusKm") radiusKm: Double? = null,
        @Query("minDistanceKm") minDistanceKm: Double? = null,
        @Query("maxDistanceKm") maxDistanceKm: Double? = null,
        @Query("status") status: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Response<ApiResponse<RouteListResponseBody>>

    @GET("routes/{routeId}")
    suspend fun getRouteDetail(@Path("routeId") routeId: String): Response<ApiResponse<RouteDetailResponseBody>>

    /**
     * S-16 커뮤니티 투표(모양 맞추기) 제출 (docs/02-api-spec.md 4장). 🔒 인증 필수 —
     * `AuthInterceptor`가 자동으로 토큰을 붙인다. 코스당 사용자 1표만 유지되며 재제출 시 교체(upsert).
     */
    @POST("routes/{routeId}/shape-votes")
    suspend fun submitShapeVote(
        @Path("routeId") routeId: String,
        @Body request: ShapeVoteRequestBody,
    ): Response<ApiResponse<ShapeVoteSubmitResponseBody>>

    @GET("routes/{routeId}/finishers")
    suspend fun getFinishers(@Path("routeId") routeId: String): Response<ApiResponse<FinishersResponseBody>>

    /** S-44 직접 그리기 — 손그림 궤적을 OSRM Map Matching으로 실도로에 스냅한다 (docs/02-api-spec.md 8.1). */
    @POST("routes/draw-convert")
    suspend fun convertDrawnPath(@Body request: RouteDrawConvertRequest): Response<ApiResponse<RouteDrawConvertResponseBody>>

    /**
     * S-45 AI 자동 생성 — 시작 위치 + 목표 거리로 순환 코스를 생성한다 (docs/02-api-spec.md 8.2).
     * 2026-09-06부로 🔒 인증 필수(일일 무료 횟수 쿼터가 사용자별이라, docs/02-api-spec.md 8.4) —
     * `AuthInterceptor`가 자동으로 토큰을 붙이므로 이 시그니처 자체는 바뀌지 않는다. 쿼터 소진 시
     * 403 `DISCOVERY_QUOTA_EXCEEDED`.
     */
    @POST("routes/discovery")
    suspend fun discoverRoute(@Body request: RouteDiscoveryRequest): Response<ApiResponse<RouteDiscoveryResponseBody>>

    /** S-45 진입 시 "오늘 남은 무료 탐색 n회" 표시용 (docs/02-api-spec.md 8.4). */
    @GET("routes/discovery/quota")
    suspend fun getDiscoveryQuota(): Response<ApiResponse<DiscoveryQuotaStatusBody>>

    /** 리워드 광고 시청 완료 콜백 — 오늘의 bonus를 +2 해서 갱신된 쿼터 상태를 돌려준다 (docs/02-api-spec.md 8.4). */
    @POST("routes/discovery/reward-unlock")
    suspend fun unlockDiscoveryReward(): Response<ApiResponse<DiscoveryQuotaStatusBody>>

    /**
     * S-45 "꼭 지나갈 장소" 검색 (docs/02-api-spec.md 12장). 인증 불필요, 항상 200 — 결과 없음과
     * 업스트림 소프트 실패를 구분하지 않고 똑같이 빈 `items`로 취급한다. `lat`/`lng`는 위치 우선순위
     * 힌트일 뿐 optional이다. debounce는 서버가 아닌 클라이언트(호출부) 책임.
     */
    @GET("routes/places/search")
    suspend fun searchPlaces(
        @Query("query") query: String,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
    ): Response<ApiResponse<PlaceSearchResponseBody>>
}

@Serializable
data class RouteListItem(
    val routeId: String,
    val name: String,
    val emoji: String,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val status: String,
    val finisherCount: Int,
    val thumbnailGeoJson: GeoJsonLineString,
    /** 로그인 상태에서만 실제 값이 오고, 비로그인 응답에서는 기본값 false로 취급한다 (docs/02-api-spec.md 4장). */
    val isSaved: Boolean = false,
)

@Serializable
data class RouteListResponseBody(
    val items: List<RouteListItem>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
)

@Serializable
data class RouteDetailResponseBody(
    val routeId: String,
    val name: String,
    val emoji: String,
    val geoJson: GeoJsonLineString,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val difficulty: String,
    val status: String,
    val finisherCount: Int,
    val trafficLightCount: Int,
    val elevationGainM: Int,
    val repeatSegmentPercent: Int,
    val runability: Double,
    val isSaved: Boolean,
    val topFeedbackTags: List<String> = emptyList(),
    /**
     * 커뮤니티 투표(모양 맞추기) 집계 — 득표율 상위 5개, 투표 없으면 빈 배열
     * (docs/02-api-spec.md 4장, v1.3 문서 297행). `name`(공식 이름)과 별개의 자유 텍스트 집계.
     */
    val shapeVotes: List<ShapeVoteTallyBody> = emptyList(),
    /** 비로그인이거나 아직 투표 안 했으면 null, 투표했으면 그 라벨. */
    val myShapeVote: String? = null,
)

/** 커뮤니티 투표 득표 한 항목 — `GET /routes/{routeId}`와 `POST .../shape-votes` 응답 공용. */
@Serializable
data class ShapeVoteTallyBody(val label: String, val percent: Int)

/** `POST /routes/{routeId}/shape-votes` 요청 — `label`은 공백 제외 1~10자(서버가 400 VALIDATION_ERROR로 검증). */
@Serializable
data class ShapeVoteRequestBody(val label: String)

@Serializable
data class ShapeVoteSubmitResponseBody(
    val shapeVotes: List<ShapeVoteTallyBody>,
    val myLabel: String,
)

@Serializable
data class FinisherThumbnail(
    val runId: String,
    val userNickname: String,
    val thumbnailGeoJson: GeoJsonLineString,
)

@Serializable
data class FinishersResponseBody(val items: List<FinisherThumbnail>)

// --- 8.1 POST /routes/draw-convert (closeLoop: docs/02-api-spec.md 11.1) ---

@Serializable
data class RouteDrawConvertRequest(
    val drawnPath: GeoJsonLineString,
    /**
     * 출발=도착 의도로 그렸을 때만 true — 기본 false(직선 코스도 허용, docs/01-feature-spec.md 1.6).
     * true인데 매칭 결과가 15m 넘게 벌어져 있으면 서버가 보정 구간을 붙여 폐곡선을 완성한다.
     */
    val closeLoop: Boolean = false,
)

@Serializable
data class RouteDrawConvertResponseBody(
    val geoJson: GeoJsonLineString,
    val distanceKm: Double,
)

// --- 8.2 POST /routes/discovery (requiredWaypoints: docs/02-api-spec.md 11.2, point-to-point: 11.4) ---

/** "꼭 지나갈 장소" 필수 경유지 하나 — 요청당 최대 3개(docs/02-api-spec.md 11.2). */
@Serializable
data class RouteWaypoint(val lat: Double, val lng: Double)

@Serializable
data class RouteDiscoveryRequest(
    val startLng: Double,
    val startLat: Double,
    val targetDistanceKm: Double,
    /** docs/01-feature-spec.md ComfortablePace의 apiValue 중 하나(예: "PACE_6_7"). optional. */
    val pace: String? = null,
    /** 비어 있으면(기본값) 필드를 그냥 빈 배열로 보낸다 — 서버가 없는 것과 동일하게 처리한다. */
    val requiredWaypoints: List<RouteWaypoint> = emptyList(),
    /** "LOOP"(기본값) | "POINT_TO_POINT" | "SHAPE" (docs/02-api-spec.md 11.4, 13.1). */
    val mode: String = "LOOP",
    /** mode가 POINT_TO_POINT일 때만 둘 다 채운다. */
    val endLat: Double? = null,
    val endLng: Double? = null,
    /**
     * mode가 SHAPE일 때만 필수 — "HEART" | "CIRCLE" | "DROP" (docs/02-api-spec.md 13.1/13.3, STAR는
     * 오목한 꼭짓점 문제로 v1에서 제외).
     * SHAPE와 [requiredWaypoints]/[endLat]/[endLng]는 함께 보낼 수 없다(13.4, 400 VALIDATION_ERROR).
     */
    val shapeType: String? = null,
    /**
     * mode가 SHAPE일 때만 의미 있음 — "S" | "M"(기본값) | "L", 모양 템플릿의 절대 크기를 직접
     * 고정한다(docs/02-api-spec.md 13.1.1). `targetDistanceKm`으로 스케일을 맞추던 방식은 모양이
     * 뭉개지는 문제로 폐기됐다 — 거리는 이 크기의 결과값이지 입력이 아니다.
     */
    val size: String? = null,
)

@Serializable
data class RouteDiscoveryResponseBody(
    val geoJson: GeoJsonLineString,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    /** 2026-09-06 추가 — 이 호출로 사용량이 반영된 뒤, 오늘 남은 생성 가능 횟수 (docs/02-api-spec.md 8.4). */
    val remainingToday: Int = 0,
)

/** `GET /routes/discovery/quota`, `POST /routes/discovery/reward-unlock` 공용 응답 (docs/02-api-spec.md 8.4). */
@Serializable
data class DiscoveryQuotaStatusBody(
    val usedToday: Int,
    val limit: Int,
    val remainingToday: Int,
)

// --- 12장 GET /routes/places/search ---

@Serializable
data class PlaceSearchItem(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
)

@Serializable
data class PlaceSearchResponseBody(val items: List<PlaceSearchItem>)
