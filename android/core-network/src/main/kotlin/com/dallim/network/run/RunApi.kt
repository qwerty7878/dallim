package com.dallim.network.run

import com.dallim.network.common.ApiResponse
import com.dallim.network.common.GeoJsonLineString
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * docs/02-api-spec.md 5장 — 러닝 모듈, 가장 중요한 API 그룹.
 * GPS는 실시간 스트리밍하지 않는다: [uploadGpsBatch]는 러닝 종료 시 Room에 쌓인 포인트를
 * WorkManager가 배치로(여러 번 나눠) 호출한다 (CLAUDE.md rule 4, GpsBatchUploadWorker).
 */
interface RunApi {
    @POST("runs")
    suspend fun startRun(@Body request: StartRunRequest): Response<ApiResponse<StartRunResponseBody>>

    @PATCH("runs/{runId}/status")
    suspend fun updateStatus(
        @Path("runId") runId: String,
        @Body request: RunStatusRequest,
    ): Response<ApiResponse<RunStatusResponseBody>>

    @POST("runs/{runId}/gps-batch")
    suspend fun uploadGpsBatch(
        @Path("runId") runId: String,
        @Body request: GpsBatchRequest,
    ): Response<ApiResponse<GpsBatchResponseBody>>

    @POST("runs/{runId}/finish")
    suspend fun finishRun(
        @Path("runId") runId: String,
        @Body request: FinishRunRequest,
    ): Response<ApiResponse<FinishRunResponseBody>>

    @GET("runs/{runId}")
    suspend fun getRun(@Path("runId") runId: String): Response<ApiResponse<RunDetailResponseBody>>

    /**
     * S-25 코스 평가(태그 선택, docs/달림_화면별_상세기획서_v1.3.md 395행) — 별점 없는 긍정 행동
     * 태그만 0~3개 제출한다. 이미 제출한 run에 다시 호출해도 idempotent(에러 아님, 기존 값 그대로
     * 응답)이므로 화면에서 제출 여부를 별도로 추적하지 않아도 안전하게 재시도할 수 있다.
     */
    @POST("runs/{runId}/feedback-tags")
    suspend fun submitFeedbackTags(
        @Path("runId") runId: String,
        @Body request: FeedbackTagsRequestBody,
    ): Response<ApiResponse<FeedbackTagsResponseBody>>

    /**
     * 2026-09-26, 사용자 요청 — 자유 러닝(코스 미선택, 나이키 런 클럽처럼 바로 시작해 완주 후
     * 실제 궤적이 그림이 되는 모드) 완주 결과를 새 코스로 공개 등록한다. [RunDetailResponseBody.routeId]가
     * null인(자유 러닝) COMPLETED 결과에서만 호출 가능 — S-25 결과 화면 참고.
     */
    @POST("runs/{runId}/register-as-route")
    suspend fun registerAsRoute(
        @Path("runId") runId: String,
        @Body request: RegisterRouteRequestBody,
    ): Response<ApiResponse<RegisterRouteResponseBody>>
}

@Serializable
data class ClientDeviceInfo(val gpsAccuracyM: Int)

/**
 * [routeId]가 null이면 자유 러닝(2026-09-26, 사용자 요청)이다 — 코스를 먼저 고르지 않고 바로
 * 달리기 시작, 완주 후 실제 궤적이 곧 그림 결과가 된다.
 */
@Serializable
data class StartRunRequest(
    val routeId: String? = null,
    val mode: String = "SOLO",
    val startedAt: String,
    val clientDeviceInfo: ClientDeviceInfo,
)

@Serializable
data class StartRunResponseBody(val runId: String, val status: String)

@Serializable
data class RunStatusRequest(val status: String)

@Serializable
data class RunStatusResponseBody(val runId: String, val status: String)

@Serializable
data class GpsPointDto(val lat: Double, val lng: Double, val timestamp: String, val accuracyM: Float)

@Serializable
data class GpsBatchRequest(val points: List<GpsPointDto>)

@Serializable
data class GpsBatchResponseBody(val receivedCount: Int)

@Serializable
data class FinishRunRequest(
    val finishedAt: String,
    val clientPrecheckStatus: String,
    // 2026-09-06, docs/달림_화면별_상세기획서_v1.3.md PART 4.1 — 저장만 해두는 값. 완주 판정에는
    // 전혀 관여하지 않는다(CLAUDE.md rule 3). 센서/권한이 없으면 null.
    val stepCount: Int? = null,
)

@Serializable
data class FinishRunResponseBody(
    val runId: String,
    val status: String,
    val distanceKm: Double,
    val durationSeconds: Int,
    val averagePaceSecPerKm: Int,
    val sketchMatchPercent: Int,
    val routeCompletionPercent: Int,
    val isFirstDiscoverer: Boolean,
    val earnedInk: Int,
    val earnedBadges: List<String> = emptyList(),
)

@Serializable
data class FeedbackTagsRequestBody(val tags: List<String>)

@Serializable
data class FeedbackTagsResponseBody(val runId: String, val tags: List<String>)

/**
 * 자유 러닝(2026-09-26)이면 [routeId]/[routeName]/[plannedGeoJson]이 전부 null이다.
 * [registeredRouteId]는 이미 "코스로 등록"됐다면 그 코스 id (자유 러닝에서만 의미 있음).
 */
@Serializable
data class RunDetailResponseBody(
    val runId: String,
    val routeId: String? = null,
    val routeName: String? = null,
    val status: String,
    val actualGeoJson: GeoJsonLineString,
    val plannedGeoJson: GeoJsonLineString? = null,
    val distanceKm: Double,
    val durationSeconds: Int,
    val averagePaceSecPerKm: Int,
    val sketchMatchPercent: Int,
    val routeCompletionPercent: Int,
    val completedAt: String,
    val stepCount: Int? = null,
    val registeredRouteId: String? = null,
)

@Serializable
data class RegisterRouteRequestBody(val name: String, val emoji: String)

@Serializable
data class RegisterRouteResponseBody(val routeId: String)
