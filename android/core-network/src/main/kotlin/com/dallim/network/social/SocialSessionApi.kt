package com.dallim.network.social

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
 * docs/02-api-spec.md 17장 — 소셜 세션 1단계 (S-30~S-34). `GET /social-sessions`와
 * `GET /social-sessions/{id}`만 인증 불필요(로그인 시 개인화), 나머지는 전부 🔒
 * (backend SocialSessionRoutes.kt와 동일 관례 — com.dallim.network.meetup.MeetupApi 참고).
 *
 * 팀채팅/체크인/Ready Check/평가/Running Mate(S-35~S-39)는 2단계 범위라 이 파일에는 없다.
 */
interface SocialSessionApi {
    /** S-30 세션 탐색 (17.1). */
    @GET("social-sessions")
    suspend fun getSocialSessions(
        @Query("routeId") routeId: String? = null,
        @Query("beginnerFriendly") beginnerFriendly: Boolean? = null,
        @Query("hasMinTemperature") hasMinTemperature: Boolean? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Response<ApiResponse<SocialSessionListResponseBody>>

    /** S-31 세션 생성 (17.2). 완주 0회면 400 `SESSION_HOST_REQUIRES_FIRST_RUN`. */
    @POST("social-sessions")
    suspend fun createSocialSession(
        @Body request: CreateSocialSessionRequest,
    ): Response<ApiResponse<CreateSocialSessionResponseBody>>

    /** S-32 세션 상세 (17.3). 비로그인/미참가자는 `meetingPointDetail`이 null. */
    @GET("social-sessions/{sessionId}")
    suspend fun getSocialSessionDetail(
        @Path("sessionId") sessionId: String,
    ): Response<ApiResponse<SocialSessionDetailResponseBody>>

    /** S-33 참가 신청 (17.4). 조건 미충족 시 400 `SESSION_CONDITION_NOT_MET`. */
    @POST("social-sessions/{sessionId}/apply")
    suspend fun applySocialSession(
        @Path("sessionId") sessionId: String,
        @Body request: ApplySocialSessionRequest,
    ): Response<ApiResponse<Unit>>

    /** 참가 신청 취소 (17.5) — PENDING 상태에서만 가능. */
    @POST("social-sessions/{sessionId}/apply/cancel")
    suspend fun cancelSocialSessionApply(
        @Path("sessionId") sessionId: String,
    ): Response<ApiResponse<Unit>>

    /** S-34 신청자 목록 (17.6). 호스트만 — 그 외는 403. */
    @GET("social-sessions/{sessionId}/applicants")
    suspend fun getSocialSessionApplicants(
        @Path("sessionId") sessionId: String,
    ): Response<ApiResponse<SocialSessionApplicantsResponseBody>>

    /** S-34 신청 승인 (17.7). 호스트만, 정원초과면 409. */
    @POST("social-sessions/{sessionId}/applicants/{userId}/approve")
    suspend fun approveSocialSessionApplicant(
        @Path("sessionId") sessionId: String,
        @Path("userId") userId: String,
    ): Response<ApiResponse<Unit>>
}

/** POST /social-sessions (17.2) request body. `genderCondition`/`rainPolicy`는 백엔드 enum과
 * 동일한 문자열 값을 그대로 주고받는다([SocialSessionGenderCondition]/[SocialSessionRainPolicy]). */
@Serializable
data class CreateSocialSessionRequest(
    val routeId: String,
    val title: String,
    val scheduledAt: String,
    val minParticipants: Int,
    val maxParticipants: Int,
    val runningStyles: List<String> = emptyList(),
    val beginnerFriendly: Boolean = false,
    val minRunningTemperature: Double? = null,
    val genderCondition: String = SocialSessionGenderCondition.ANY,
    val description: String? = null,
    val meetingPointLat: Double,
    val meetingPointLng: Double,
    val meetingPointDescription: String? = null,
    val rainPolicy: String = SocialSessionRainPolicy.DECIDE_LATER,
)

@Serializable
data class CreateSocialSessionResponseBody(val sessionId: String)

/** GET /social-sessions (17.1) 아이템 — S-30 세션 카드. */
@Serializable
data class SocialSessionListItem(
    val sessionId: String,
    val title: String,
    val scheduledAt: String,
    val routeId: String,
    val routeThumbnailGeoJson: GeoJsonLineString?,
    val approvedCount: Int,
    val minParticipants: Int,
    val maxParticipants: Int,
    val runningStyles: List<String>,
    val hostUserId: String,
    val hostNickname: String,
    val hostAvatarId: String?,
    val hostRunningTemperature: Double,
    val beginnerFriendly: Boolean,
    val status: String,
)

@Serializable
data class SocialSessionListResponseBody(
    val items: List<SocialSessionListItem>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
)

/** 참가자 카드 공통 모양(GET .../{id}의 `participants`) — gender는 절대 포함하지 않는다
 * (CLAUDE.md 규칙 2). */
@Serializable
data class SocialSessionParticipantItem(
    val userId: String,
    val nickname: String,
    val avatarId: String?,
)

/** GET /social-sessions/{id} (17.3) — S-32 세션 상세. */
@Serializable
data class SocialSessionDetailResponseBody(
    val sessionId: String,
    val hostUserId: String,
    val hostNickname: String,
    val hostAvatarId: String?,
    val hostRunningTemperature: Double,
    val routeId: String,
    val routeName: String,
    val routeThumbnailGeoJson: GeoJsonLineString?,
    val routeDistanceKm: Double?,
    val routeEstimatedMinutes: Int?,
    val title: String,
    val scheduledAt: String,
    val minParticipants: Int,
    val maxParticipants: Int,
    val approvedCount: Int,
    val runningStyles: List<String>,
    val beginnerFriendly: Boolean,
    val minRunningTemperature: Double?,
    val genderCondition: String,
    val description: String?,
    val meetingPointLat: Double,
    val meetingPointLng: Double,
    // 호스트/APPROVED 참가자에게만 채워짐, 그 외엔 null.
    val meetingPointDetail: String?,
    // 항상 채워짐 — 대략적인 지역 힌트.
    val meetingPointHint: String,
    val rainPolicy: String,
    val status: String,
    val isHost: Boolean,
    // null | "PENDING" | "APPROVED" | "EXPIRED" | "CANCELLED"
    val myApplicationStatus: String?,
    val participants: List<SocialSessionParticipantItem>,
)

/** POST /social-sessions/{id}/apply (17.4) request body. */
@Serializable
data class ApplySocialSessionRequest(val message: String? = null)

/** GET /social-sessions/{id}/applicants (17.6) 아이템 — S-34. 성별/나이/연락처는 응답에
 * 없다(SPEC 명시) — 표시하려 하지 않는다. */
@Serializable
data class SocialSessionApplicantItem(
    val userId: String,
    val nickname: String,
    val avatarId: String?,
    val runningTemperature: Double,
    val comfortablePace: String?,
    val runningExperience: String?,
    val message: String?,
    val appliedAt: String,
    val status: String,
)

@Serializable
data class SocialSessionApplicantsResponseBody(val items: List<SocialSessionApplicantItem>)

/** 성별 조건 4종 (17.2) — S-31 폼/뱃지에서 문자열 상수로 비교할 때 쓴다. */
object SocialSessionGenderCondition {
    const val ANY = "ANY"
    const val SAME_AS_HOST = "SAME_AS_HOST"
    const val FEMALE_ONLY = "FEMALE_ONLY"
    const val MALE_ONLY = "MALE_ONLY"
}

/** 우천 시 정책 3종 (17.2). */
object SocialSessionRainPolicy {
    const val PROCEED = "PROCEED"
    const val CANCEL = "CANCEL"
    const val DECIDE_LATER = "DECIDE_LATER"
}

/** 화면에 보이는 계산된 상태(17.1/17.3 `status`) — com.dallim.social.SocialSessionDisplayStatus와
 * 동일한 문자열 값. */
object SocialSessionDisplayStatus {
    const val RECRUITING = "RECRUITING"
    const val NEAR_CONFIRMATION = "NEAR_CONFIRMATION"
    const val CONFIRMED = "CONFIRMED"
    const val CANCELLED = "CANCELLED"
}

/** 참가 신청 상태(17.3 `myApplicationStatus`/17.6 `status`). */
object SocialSessionApplicantStatus {
    const val PENDING = "PENDING"
    const val APPROVED = "APPROVED"
    const val EXPIRED = "EXPIRED"
    const val CANCELLED = "CANCELLED"
}
