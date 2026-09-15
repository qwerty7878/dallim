package com.dallim.social

import com.dallim.common.GeoJsonLineString
import kotlinx.serialization.Serializable

// GET /users/me/social-sessions (채팅 인박스) — docs/02-api-spec.md 18장. gender는 여기에도 없다
// (CLAUDE.md 규칙 2).

/** SYSTEM 메시지 대비 [senderNickname]은 nullable. */
@Serializable
data class MySocialSessionLastMessage(
    val body: String,
    val type: ChatMessageType,
    val createdAt: String,
    val senderNickname: String?,
)

/** 읽음/안읽음 카운트는 이번 라운드 범위 밖 -- 마지막 읽은 시각을 저장하는 인프라가 전혀 없다
 * (과설계 금지, docs/02-api-spec.md 18.3 참고). */
@Serializable
data class MySocialSessionListItem(
    val sessionId: String,
    val title: String,
    val routeThumbnailGeoJson: GeoJsonLineString?,
    val scheduledAt: String,
    val isHost: Boolean,
    val approvedCount: Int,
    val maxParticipants: Int,
    val status: SocialSessionDisplayStatus,
    // 채팅이 한 번도 없었으면 null.
    val lastMessage: MySocialSessionLastMessage?,
)

/** 페이지네이션 없음 -- 한 유저가 동시에 속한 세션 수는 자연히 적다(최대 몇십 개 수준 가정,
 * 과설계 금지). */
@Serializable
data class MySocialSessionListResponse(val items: List<MySocialSessionListItem>)
