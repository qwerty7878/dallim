package com.dallim.social

import kotlinx.serialization.Serializable

// Request/response DTOs — docs/02-api-spec.md 17장 이어서 (S-36/S-37). gender는 여기에도 없다
// (CLAUDE.md 규칙 2).

@Serializable
data class CheckinRequest(val lat: Double, val lng: Double)

/** POST /social-sessions/{id}/checkin 성공 응답 -- distanceToMeetingPointM은 "집결지까지 80m"
 * 표시용(성공했을 때만 내려준다, 실패는 ApiErrorBody가 code/message만 갖는 기존 스키마를 그대로
 * 따른다 -- 과설계 금지, 작업 브리핑 참고). */
@Serializable
data class CheckinResponse(
    val status: SocialSessionCheckinStatus,
    val distanceToMeetingPointM: Double,
    val checkedInAt: String?,
)

@Serializable
data class ReadyCheckItem(
    val userId: String,
    val nickname: String,
    val avatarId: String?,
    val isHost: Boolean,
    val status: SocialSessionCheckinStatus,
    val checkedInAt: String?,
    val distanceErrorM: Double?,
    val manualByHost: Boolean,
)

@Serializable
data class ReadyCheckResponse(
    val items: List<ReadyCheckItem>,
    val started: Boolean,
)
