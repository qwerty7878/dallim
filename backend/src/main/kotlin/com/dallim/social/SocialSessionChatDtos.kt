package com.dallim.social

import kotlinx.serialization.Serializable

// Request/response DTOs — docs/02-api-spec.md 17장 이어서 (S-35). gender는 여기에도 없다
// (CLAUDE.md 규칙 2).

@Serializable
data class ChatMessageItem(
    val id: String,
    val senderUserId: String?,
    // 시스템 메시지(senderUserId == null)면 항상 null -- 클라이언트가 "시스템" 라벨을 직접 붙인다.
    val senderNickname: String?,
    val senderAvatarId: String?,
    val type: ChatMessageType,
    val body: String,
    val createdAt: String,
)

/** GET .../chat/messages — 최신 순 페이지네이션(com.dallim.notification.NotificationRepository
 * .findPage와 동일 관례), WebSocket 연결 전/재접속 시 과거 메시지 불러오기용. */
@Serializable
data class ChatMessageListResponse(
    val items: List<ChatMessageItem>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
)

/** WebSocket 텍스트 프레임(클라이언트 -> 서버)으로 받는 바디. */
@Serializable
data class IncomingChatFrame(
    val type: IncomingChatMessageType,
    val body: String,
    // QuickMessages.CANT_MAKE_IT("오늘 참가 어려워요")에만 의미 있음 -- 채팅 테이블에는 저장하지
    // 않고, 값이 있으면 이 사유를 담은 SYSTEM 메시지 하나를 추가로 만들어 브로드캐스트한다
    // (새 컬럼을 만들지 않기 위한 선택 -- 과설계 금지, 작업 브리핑 참고).
    val cancelReason: String? = null,
)

/** 보낸 사람 한 명에게만 돌려주는 에러 프레임(브로드캐스트하지 않음). */
@Serializable
data class ChatErrorFrame(
    val code: String,
    val message: String,
)

/** 채팅 메시지 신고(POST .../chat/messages/{messageId}/report) / 세션 신고
 * (POST /social-sessions/{id}/report) 공통 요청 바디. */
@Serializable
data class ReportRequest(val reason: String? = null)
