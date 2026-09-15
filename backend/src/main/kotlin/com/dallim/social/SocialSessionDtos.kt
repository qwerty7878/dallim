package com.dallim.social

import com.dallim.common.GeoJsonLineString
import kotlinx.serialization.Serializable

// Request/response DTOs — docs/02-api-spec.md 17장. gender는 어떤 응답 DTO에도 없다
// (CLAUDE.md 규칙 2) -- 호스트/참가자 카드는 닉네임/아바타/runningTemperature까지만 노출한다.

/** POST /social-sessions (17.2) request body. */
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
    val genderCondition: SocialSessionGenderCondition = SocialSessionGenderCondition.ANY,
    val description: String? = null,
    val meetingPointLat: Double,
    val meetingPointLng: Double,
    val meetingPointDescription: String? = null,
    val rainPolicy: SocialSessionRainPolicy = SocialSessionRainPolicy.DECIDE_LATER,
)

@Serializable
data class CreateSocialSessionResponse(val sessionId: String)

/** GET /social-sessions (17.1) list item -- S-30 세션 카드. `targetPace`는 없다 (SPEC이 정확한
 * 값 목록을 안 줘서 [runningStyles] 자유 텍스트로 대체, 작업 브리핑 참고). */
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
    val status: SocialSessionDisplayStatus,
)

@Serializable
data class SocialSessionListResponse(
    val items: List<SocialSessionListItem>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
)

/** GET /social-sessions/{id}'s `participants` (APPROVED만) / .../applicants 응답 아이템의 공통
 * 카드 모양. */
@Serializable
data class SocialSessionParticipantItem(
    val userId: String,
    val nickname: String,
    val avatarId: String?,
)

/** GET /social-sessions/{id} (17.3) — S-32 세션 상세. */
@Serializable
data class SocialSessionDetailResponse(
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
    val genderCondition: SocialSessionGenderCondition,
    val description: String?,
    val meetingPointLat: Double,
    val meetingPointLng: Double,
    // 로그인 유저가 APPROVED 참가자이거나 호스트 본인일 때만 채워짐, 그 외엔 null (17.3 참고).
    val meetingPointDetail: String?,
    // 항상 채워짐 -- 대략적인 지역 힌트 (meetingPointDetail이 null일 때의 대체 정보).
    val meetingPointHint: String,
    val rainPolicy: SocialSessionRainPolicy,
    val status: SocialSessionDisplayStatus,
    val isHost: Boolean,
    // 비로그인이거나 신청한 적 없으면 null. 호스트는 참가자가 아니므로 항상 null.
    val myApplicationStatus: SocialSessionApplicantStatus?,
    val participants: List<SocialSessionParticipantItem>,
    // 신청 가능 여부(2026-09-16 사용자 지시) -- `now <= scheduledAt - 3일`. 안드로이드가 이 값으로
    // "신청하기" 버튼을 미리 비활성화할 수 있다. false여도 실제 신청 시도는 서버가
    // SESSION_APPLY_WINDOW_CLOSED로 다시 막는다(클라이언트 값은 UX 프리뷰일 뿐).
    val applicationOpen: Boolean,
)

/** POST /social-sessions/{id}/apply (17.4) request body. */
@Serializable
data class ApplySocialSessionRequest(val message: String? = null)

/** GET /social-sessions/{id}/applicants (17.6) item — S-34. 성별/나이/연락처 없음(SPEC 명시).
 * 참석률/소셜 달림 횟수/긍정 행동 태그/과거 동반 여부는 2단계(체크인/피드백)가 있어야 계산
 * 가능해서 이번 1단계 응답에는 없음(없는 데이터를 placeholder로 채우지 않음). */
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
    val status: SocialSessionApplicantStatus,
)

@Serializable
data class SocialSessionApplicantsResponse(val items: List<SocialSessionApplicantItem>)
