package com.dallim.network.race

import com.dallim.network.common.ApiResponse
import com.dallim.network.common.GeoJsonLineString
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * docs/02-api-spec.md 16장 — 대회 캘린더(S-80 목록/S-81 상세). "앞으로 열릴 대회 정보를
 * 찾아보고 담아두는" 신규 도메인으로, `com.dallim.network.racerecord`(15장 — 내가 과거에 뛴
 * 대회의 자기신고 완주 이력)와 완전히 별개다. 혼동 금지.
 */
interface RaceApi {
    /**
     * S-80 대회 목록 — 비로그인도 조회 가능, `isSaved`만 옵셔널 JWT로 개인화(4장 `GET /routes`와
     * 동일한 패턴). 기본 정렬은 서버가 이미 접수 마감 임박순으로 내려주므로 클라이언트에서
     * 재정렬하지 않는다.
     */
    @GET("races")
    suspend fun getRaces(
        @Query("region") region: String? = null,
        @Query("category") category: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Response<ApiResponse<RaceListResponseBody>>

    /** S-81 대회 상세 — 종목별(거리·참가비·정원·컷오프) 표 포함. */
    @GET("races/{raceId}")
    suspend fun getRaceDetail(@Path("raceId") raceId: String): Response<ApiResponse<RaceDetailResponseBody>>

    /** 내 대회에 담기 — idempotent(이미 담았어도 에러 아님). */
    @POST("races/{raceId}/save")
    suspend fun saveRace(@Path("raceId") raceId: String): Response<ApiResponse<Unit>>

    /** 내 대회 담기 취소 — idempotent(담지 않았어도 에러 아님). */
    @DELETE("races/{raceId}/save")
    suspend fun unsaveRace(@Path("raceId") raceId: String): Response<ApiResponse<Unit>>

    /** 내가 담은 대회 목록 — 대회 날짜(raceDate) 임박순, 16.2 아이템과 동일한 형태. */
    @GET("users/me/races")
    suspend fun getMyRaces(): Response<ApiResponse<MyRacesResponseBody>>

    /**
     * S-85 대회 코스 미리 달리기 — 16.6(신규). 이 대회에 공식 코스가 없으면 `hasCourse=false`뿐이고
     * 나머지 필드는 null/빈 배열이다. "이 구간 달리기"는 별도 엔드포인트가 아니라
     * `segments[].routeId`로 기존 `POST /runs`(S-20 러닝 준비)를 그대로 호출하는 것이다.
     */
    @GET("races/{raceId}/course")
    suspend fun getRaceCourse(@Path("raceId") raceId: String): Response<ApiResponse<RaceCourseResponseBody>>
}

/**
 * `category`는 5K/10K/HALF/FULL/ULTRA/TRAIL 중 하나를 나타내는 자유 문자열이다 — 이 앱 전역
 * 관례(status/mode 등)를 따라 Kotlin enum으로 감싸지 않는다(과설계 금지).
 */
@Serializable
data class RaceSummaryItem(
    val raceId: String,
    val name: String,
    val region: String,
    val location: String,
    val raceDate: String,
    val dDay: Int,
    val registrationStart: String,
    val registrationEnd: String,
    /** UPCOMING | OPEN | CLOSED — 서버가 현재 시각 기준으로 계산해서 내려준다. */
    val status: String,
    val categories: List<String>,
    val minFeeKrw: Int? = null,
    val maxFeeKrw: Int? = null,
    val savedCount: Int,
    val isSaved: Boolean = false,
    // S-85(코스 미리 달리기) 완주 진행률 — 공식 코스가 없으면 null, 있으면 0~100. 비로그인이면 0.
    val previewProgressPercent: Int? = null,
)

@Serializable
data class RaceListResponseBody(
    val items: List<RaceSummaryItem>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
)

/** 대회 상세의 종목별 한 항목 — 목록의 `categories`(문자열 배열)와 달리 종목별 상세 필드를 갖는다. */
@Serializable
data class RaceCategoryDetail(
    val category: String,
    val distanceKm: Double? = null,
    val feeKrw: Int? = null,
    val capacity: Int? = null,
    val cutoffMinutes: Int? = null,
)

@Serializable
data class RaceDetailResponseBody(
    val raceId: String,
    val name: String,
    val region: String,
    val location: String,
    val raceDate: String,
    val dDay: Int,
    val registrationStart: String,
    val registrationEnd: String,
    val status: String,
    val organizer: String,
    val souvenir: String? = null,
    val categories: List<RaceCategoryDetail>,
    val minFeeKrw: Int? = null,
    val maxFeeKrw: Int? = null,
    val savedCount: Int,
    val isSaved: Boolean = false,
    val previewProgressPercent: Int? = null,
)

@Serializable
data class MyRacesResponseBody(val items: List<RaceSummaryItem>)

/** GET /races/{raceId}/course 구간 아이템 — S-85. `routeId`가 곧 "이 구간 달리기" 액션이다. */
@Serializable
data class RaceCourseSegmentItem(
    val segmentId: String,
    val label: String,
    val routeId: String,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val elevationGainM: Int,
    val orderIndex: Int,
    val isCompleted: Boolean = false,
)

/**
 * GET /races/{raceId}/course — S-85 "대회 코스 미리 달리기". `hasCourse=false`면 나머지 필드는
 * 전부 null/빈 배열이다.
 */
@Serializable
data class RaceCourseResponseBody(
    val hasCourse: Boolean,
    val geoJson: GeoJsonLineString? = null,
    val distanceKm: Double? = null,
    val elevationGainM: Int? = null,
    val segments: List<RaceCourseSegmentItem> = emptyList(),
    val previewProgressPercent: Int? = null,
)
