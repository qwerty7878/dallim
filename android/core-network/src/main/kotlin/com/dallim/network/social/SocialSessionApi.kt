package com.dallim.network.social

import com.dallim.network.common.ApiResponse
import com.dallim.network.common.GeoJsonLineString
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * docs/02-api-spec.md 17장 — 소셜 세션 1단계(S-30~S-34) + 2단계(S-35~S-39, 팀채팅/체크인/
 * Ready Check/평가/Running Mate). `GET /social-sessions`와 `GET /social-sessions/{id}`만 인증
 * 불필요(로그인 시 개인화), 나머지는 전부 🔒 (backend SocialSessionRoutes.kt와 동일 관례 —
 * com.dallim.network.meetup.MeetupApi 참고). 팀채팅 WebSocket(`GET .../chat/ws`)은 Retrofit으로
 * 표현할 수 없어 별도 [SocialSessionChatSocket]으로 분리돼 있다.
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

    // --- 2단계 (S-35~S-39, docs/02-api-spec.md 17.11 이하) ---

    /** S-35 팀 채팅 히스토리 (17.11). 최신순. 미참가자는 403 `SESSION_NOT_PARTICIPANT`, 시작
     * +7일 경과는 403 `SESSION_CHAT_ACCESS_EXPIRED`. */
    @GET("social-sessions/{sessionId}/chat/messages")
    suspend fun getChatMessages(
        @Path("sessionId") sessionId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 30,
    ): Response<ApiResponse<ChatMessageListResponseBody>>

    /** S-35 채팅 메시지 신고 (17.13). 메시지가 없으면 404 `CHAT_MESSAGE_NOT_FOUND`. */
    @POST("social-sessions/{sessionId}/chat/messages/{messageId}/report")
    suspend fun reportChatMessage(
        @Path("sessionId") sessionId: String,
        @Path("messageId") messageId: String,
        @Body request: SocialReportRequest,
    ): Response<ApiResponse<Unit>>

    /** S-35 세션 자체 신고 (17.13). */
    @POST("social-sessions/{sessionId}/report")
    suspend fun reportSocialSession(
        @Path("sessionId") sessionId: String,
        @Body request: SocialReportRequest,
    ): Response<ApiResponse<Unit>>

    /** S-36 GPS 체크인 (17.14). 시간 윈도우 밖 400 `SESSION_CHECKIN_OUTSIDE_WINDOW`, 반경 밖
     * 400 `SESSION_CHECKIN_TOO_FAR`, 이미 시작됨 400 `SESSION_ALREADY_STARTED`. */
    @POST("social-sessions/{sessionId}/checkin")
    suspend fun checkinSocialSession(
        @Path("sessionId") sessionId: String,
        @Body request: SocialSessionCheckinRequest,
    ): Response<ApiResponse<SocialSessionCheckinResponseBody>>

    /** S-37 Ready Check 현황판 (17.15). 호스트+`APPROVED` 참가자만. */
    @GET("social-sessions/{sessionId}/ready-check")
    suspend fun getReadyCheck(
        @Path("sessionId") sessionId: String,
    ): Response<ApiResponse<SocialSessionReadyCheckResponseBody>>

    /** S-37 호스트가 "달리기 시작" (17.15). 중복 시작은 409 `SESSION_ALREADY_STARTED`. */
    @POST("social-sessions/{sessionId}/start")
    suspend fun startSocialSession(
        @Path("sessionId") sessionId: String,
    ): Response<ApiResponse<Unit>>

    /** S-37 호스트의 수동 체크인 확인 (17.15) — GPS 오차/NO_SHOW 이의제기 공용. */
    @POST("social-sessions/{sessionId}/checkins/{userId}/manual-confirm")
    suspend fun manualConfirmCheckin(
        @Path("sessionId") sessionId: String,
        @Path("userId") userId: String,
    ): Response<ApiResponse<Unit>>

    /** S-38 평가 대상 목록 (17.16) — 나 자신 제외, 같이 체크인한 사람만. */
    @GET("social-sessions/{sessionId}/feedback-targets")
    suspend fun getFeedbackTargets(
        @Path("sessionId") sessionId: String,
    ): Response<ApiResponse<SocialSessionFeedbackTargetsResponseBody>>

    /** S-38 평가 제출 (17.16) — 재제출은 덮어쓰기. */
    @POST("social-sessions/{sessionId}/feedback")
    suspend fun submitSocialSessionFeedback(
        @Path("sessionId") sessionId: String,
        @Body request: SubmitSocialSessionFeedbackRequest,
    ): Response<ApiResponse<SubmitSocialSessionFeedbackResponseBody>>

    /** S-39 내 Running Mate 목록 (17.17). */
    @GET("users/me/running-mates")
    suspend fun getRunningMates(): Response<ApiResponse<RunningMateListResponseBody>>

    /** S-39 Running Mate 조용한 단방향 해제 (17.17) — 상대에게 알림 없음, 멱등. */
    @DELETE("users/me/running-mates/{mateUserId}")
    suspend fun deleteRunningMate(
        @Path("mateUserId") mateUserId: String,
    ): Response<ApiResponse<Unit>>

    /**
     * 채팅 인박스 (2026-09-16 신규, v1.3 SPEC 밖 — docs/02-api-spec.md 18.1). 내가 호스트이거나
     * `APPROVED` 참가자인 모든 소셜 세션. 페이지네이션 없음.
     */
    @GET("users/me/social-sessions")
    suspend fun getSocialSessionInbox(): Response<ApiResponse<SocialSessionInboxResponseBody>>
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

// ======================================================================================
// 2단계 (S-35~S-39, docs/02-api-spec.md 17.10 이하) — DTO/상수. backend
// com.dallim.social.{SocialSessionChatDtos,SocialSessionCheckinDtos,SocialSessionFeedbackDtos,
// RunningMateDtos}.kt와 동일한 필드명. 다른 enum 필드(genderCondition/rainPolicy/status)와
// 동일하게 Kotlin enum이 아니라 String + 상수 오브젝트로 받는다(알 수 없는 값이 와도
// `ignoreUnknownKeys`처럼 안전하게 무시/폴백할 수 있게 하려는 기존 관례를 그대로 따름).
// ======================================================================================

/** GET/WS `.../chat/messages`, `.../chat/ws` (17.11/17.12) 메시지 한 건. 시스템 메시지
 * (`type == SYSTEM`)는 `senderUserId`/`senderNickname`/`senderAvatarId` 모두 null. */
@Serializable
data class ChatMessageItem(
    val id: String,
    val senderUserId: String?,
    val senderNickname: String?,
    val senderAvatarId: String?,
    val type: String,
    val body: String,
    val createdAt: String,
)

@Serializable
data class ChatMessageListResponseBody(
    val items: List<ChatMessageItem>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
)

/** 채팅 메시지 신고 / 세션 신고(17.13) 공통 요청 바디. */
@Serializable
data class SocialReportRequest(val reason: String? = null)

/** `ChatMessageItem.type` (17.10) 4종. */
object SocialChatMessageType {
    const val TEXT = "TEXT"
    const val QUICK_MESSAGE = "QUICK_MESSAGE"
    const val HOST_ANNOUNCEMENT = "HOST_ANNOUNCEMENT"
    const val SYSTEM = "SYSTEM"
}

/** Quick Message 고정 4문구(17.12) — 이 문자열 그대로가 아니면 서버가 `VALIDATION_ERROR`로
 * 거부한다. backend `com.dallim.social.QuickMessages`와 정확히 동일해야 한다. */
object SocialQuickMessages {
    const val ARRIVED = "도착했어요"
    const val ON_MY_WAY = "가는 중이에요"
    const val FIVE_MIN_LATE = "5분 늦어요"
    const val CANT_MAKE_IT = "오늘 참가 어려워요"

    val ALL: List<String> = listOf(ARRIVED, ON_MY_WAY, FIVE_MIN_LATE, CANT_MAKE_IT)
}

/** POST `.../checkin` (17.14) request body. */
@Serializable
data class SocialSessionCheckinRequest(val lat: Double, val lng: Double)

@Serializable
data class SocialSessionCheckinResponseBody(
    val status: String,
    val distanceToMeetingPointM: Double,
    val checkedInAt: String?,
)

/** `SocialSessionCheckinResponseBody.status`/`SocialSessionReadyCheckItem.status` (17.10) 4종. */
object SocialSessionCheckinStatus {
    const val WAITING = "WAITING"
    const val CHECKED_IN = "CHECKED_IN"
    const val LATE = "LATE"
    const val NO_SHOW = "NO_SHOW"
}

/** GET `.../ready-check` (17.15) 아이템 한 명. */
@Serializable
data class SocialSessionReadyCheckItem(
    val userId: String,
    val nickname: String,
    val avatarId: String?,
    val isHost: Boolean,
    val status: String,
    val checkedInAt: String?,
    val distanceErrorM: Double?,
    val manualByHost: Boolean,
)

@Serializable
data class SocialSessionReadyCheckResponseBody(
    val items: List<SocialSessionReadyCheckItem>,
    val started: Boolean,
)

/** GET `.../feedback-targets` (17.16) 아이템 — 나 자신 제외, 같이 체크인한 사람만. */
@Serializable
data class SocialSessionFeedbackTargetItem(
    val userId: String,
    val nickname: String,
    val avatarId: String?,
    val isHost: Boolean,
)

@Serializable
data class SocialSessionFeedbackTargetsResponseBody(val items: List<SocialSessionFeedbackTargetItem>)

/** POST `.../feedback` (17.16) request body. `tags`는 [SocialFeedbackTags.ALL]에서 최대
 * [SocialFeedbackTags.MAX_TAGS]개, 빈 배열(미선택)도 허용. */
@Serializable
data class SubmitSocialSessionFeedbackRequest(
    val targetUserId: String,
    val tags: List<String> = emptyList(),
    val wantsToRunAgain: Boolean = false,
)

@Serializable
data class SubmitSocialSessionFeedbackResponseBody(val mateEstablished: Boolean)

/** 사람 평가 긍정 태그 전용 어휘집(17.16) — 별점/부정 태그 없음. backend
 * `com.dallim.social.SocialFeedbackTags.ALLOWED`와 정확히 동일해야 한다. */
object SocialFeedbackTags {
    const val ON_TIME = "시간을 잘 지켜요"
    const val GOOD_PACE = "페이스를 잘 맞춰줘요"
    const val SAFE = "안전하게 달렸어요"
    const val KIND = "친절했어요"
    const val FUN_VIBE = "분위기를 즐겁게 만들어요"
    const val KNOWS_COURSE = "코스 정보를 잘 알려줬어요"

    val ALL: List<String> = listOf(ON_TIME, GOOD_PACE, SAFE, KIND, FUN_VIBE, KNOWS_COURSE)
    const val MAX_TAGS = 3
}

/** GET `/users/me/running-mates` (17.17) 아이템 한 명. */
@Serializable
data class RunningMateItem(
    val userId: String,
    val nickname: String,
    val avatarId: String?,
    val runTogetherCount: Int,
    val lastRunTogetherAt: String,
)

@Serializable
data class RunningMateListResponseBody(val items: List<RunningMateItem>)

// ======================================================================================
// 채팅 인박스 (2026-09-16 신규, v1.3 SPEC 밖 — docs/02-api-spec.md 18.1). "채팅" 탭에 올릴
// 목록이 필요하다는 이 세션의 판단을 사용자가 승인했다.
// ======================================================================================

/** `GET /users/me/social-sessions` (18.1) 세션 하나의 마지막 채팅 메시지 미리보기.
 * `type == SYSTEM`이면 `senderNickname`도 null. */
@Serializable
data class SocialSessionInboxLastMessage(
    val body: String,
    val type: String,
    val createdAt: String,
    val senderNickname: String?,
)

/** `GET /users/me/social-sessions` (18.1) 아이템 — 채팅 탭 목록 한 행. 정렬은 서버가
 * `lastMessage.createdAt` 내림차순(채팅 없던 세션은 뒤로) + `scheduledAt` 오름차순으로 이미
 * 해준다 — 클라이언트가 재정렬하지 않는다. */
@Serializable
data class SocialSessionInboxItem(
    val sessionId: String,
    val title: String,
    val routeThumbnailGeoJson: GeoJsonLineString?,
    val scheduledAt: String,
    val isHost: Boolean,
    val approvedCount: Int,
    val maxParticipants: Int,
    val status: String,
    val lastMessage: SocialSessionInboxLastMessage?,
)

@Serializable
data class SocialSessionInboxResponseBody(val items: List<SocialSessionInboxItem>)
