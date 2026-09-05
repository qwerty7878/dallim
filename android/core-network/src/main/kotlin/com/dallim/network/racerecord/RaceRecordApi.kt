package com.dallim.network.racerecord

import com.dallim.network.common.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * docs/02-api-spec.md 15장 — 러닝 커리어(완주 이력) CRUD + 페이스 제안. 백엔드는 이미 구현되어
 * 있으며(`backend/src/main/kotlin/com/dallim/racerecord/RaceRecordDtos.kt`) 이 파일은 그 계약을
 * 그대로 옮긴 것 — 백엔드 코드는 이 라운드에서 건드리지 않는다.
 *
 * `category`/`recordType`은 [UserApi]의 `runningExperience`/`comfortablePace`와 동일한 관례로,
 * 응답에서도 실제 enum이 아니라 자유 문자열로 받는다(백엔드는 kotlinx.serialization enum을
 * 그대로 직렬화하지만, 안드로이드 쪽은 이 앱 전역 관례상 응답 enum 필드를 String으로 받고 UI
 * 매핑은 별도 옵션 enum(`com.dallim.app.career.RaceCategoryOption` 등)에서 라벨을 붙인다 —
 * `UserMeResponseBody.runningExperience`/`comfortablePace`와 동일 패턴).
 */
interface RaceRecordApi {
    /** S-91 메달 선반 — 연도 내림차순(동일 연도는 최신 등록순, 서버가 이미 정렬). */
    @GET("users/me/race-records")
    suspend fun getRaceRecords(): Response<ApiResponse<RaceRecordListResponseBody>>

    /** S-04b/S-90 등록 — 응답에 생성 직후 계산된 `isPb`/`paceSuggestion`까지 포함된다(15.3). */
    @POST("users/me/race-records")
    suspend fun createRaceRecord(@Body request: CreateRaceRecordRequest): Response<ApiResponse<RaceRecordItem>>

    /** S-90 수정 — 부분 수정, 요청에 없는 필드는 기존 값 유지(15.4). */
    @PATCH("users/me/race-records/{id}")
    suspend fun updateRaceRecord(
        @Path("id") id: String,
        @Body request: UpdateRaceRecordRequest,
    ): Response<ApiResponse<RaceRecordItem>>

    /** S-90 삭제 (15.5). 본인 이력이 아니거나 존재하지 않으면 404 RACE_RECORD_NOT_FOUND. */
    @DELETE("users/me/race-records/{id}")
    suspend fun deleteRaceRecord(@Path("id") id: String): Response<ApiResponse<Unit>>

    /**
     * S-04b/S-90 "기록 → 예상 페이스 환산" 단독 호출(15.6). `category`가 HALF/FULL이 아니거나
     * `recordSeconds`가 0 이하면 400 VALIDATION_ERROR.
     */
    @POST("users/me/race-records/pace-suggestion")
    suspend fun suggestPace(@Body request: PaceSuggestionRequest): Response<ApiResponse<PaceSuggestionResponseBody>>
}

@Serializable
data class CreateRaceRecordRequest(
    val raceName: String,
    val category: String,
    val distanceKm: Double? = null,
    val year: Int,
    val recordSeconds: Int? = null,
    val recordType: String? = null,
    val bibNumber: String? = null,
    val memo: String? = null,
)

/** 부분 수정 — null인 필드는 기존 값 유지(15.4). */
@Serializable
data class UpdateRaceRecordRequest(
    val raceName: String? = null,
    val category: String? = null,
    val distanceKm: Double? = null,
    val year: Int? = null,
    val recordSeconds: Int? = null,
    val recordType: String? = null,
    val bibNumber: String? = null,
    val memo: String? = null,
)

/**
 * 목록/생성/수정 응답 공통 아이템(15.2~15.4). [verified]는 자기신고 이력이라 이번 라운드에는
 * 항상 `false` — "인증됨"으로 오해할 수 있는 표현을 화면에 절대 쓰지 않는다. [isPb]/
 * [paceSuggestion]은 서버가 매 요청마다 계산해서 내려주는 값이라 클라이언트에서 재계산하지 않는다.
 */
@Serializable
data class RaceRecordItem(
    val id: String,
    val raceName: String,
    val category: String,
    val distanceKm: Double?,
    val year: Int,
    val recordSeconds: Int?,
    val recordType: String?,
    val bibNumber: String?,
    val memo: String?,
    val verified: Boolean,
    val isPb: Boolean,
    val paceSuggestion: String?,
    val createdAt: String,
)

@Serializable
data class RaceRecordListResponseBody(val items: List<RaceRecordItem>)

@Serializable
data class PaceSuggestionRequest(
    val category: String,
    val recordSeconds: Int,
)

/** [suggestedPace]는 `com.dallim.app.onboarding.profile.ComfortablePace.apiValue`와 동일한 값 세트. */
@Serializable
data class PaceSuggestionResponseBody(val suggestedPace: String)
