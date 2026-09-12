package com.dallim.social

import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * 팀 채팅 (S-35, 2단계) — docs/02-api-spec.md 17장 이어서. Ktor 네이티브 WebSocket + 서버
 * 프로세스 내 인메모리 커넥션 맵(SocialSessionChatHub)으로 구현한다. EC2 단일 인스턴스라
 * Kafka/RabbitMQ/Redis Pub-Sub 전부 불필요 -- 인스턴스가 여러 대가 되면 SocialSessionChatHub
 * 내부만 Redis Pub/Sub로 바꾸면 되는 구조이되, 지금 그 추상화를 미리 만들지는 않는다(과설계 금지,
 * 작업 브리핑 참고).
 */
enum class ChatMessageType {
    TEXT,
    QUICK_MESSAGE,
    HOST_ANNOUNCEMENT,
    SYSTEM,
}

/**
 * 클라이언트가 WebSocket으로 직접 요청할 수 있는 타입의 부분집합 -- HOST_ANNOUNCEMENT(호스트의
 * TEXT를 서버가 자동 승격시킨 결과)와 SYSTEM(서버 전용, 예: 취소 사유 안내)은 클라이언트가 직접
 * 고를 수 없다. SocialSessionChatService.handleIncoming 참고.
 */
enum class IncomingChatMessageType {
    TEXT,
    QUICK_MESSAGE,
}

/** Quick Message 고정 문구 4종 (S-35) — `type=QUICK_MESSAGE`일 때 body는 이 중 하나가 아니면
 * 400 VALIDATION_ERROR (com.dallim.run.RouteFeedbackTags와 동일한 "고정 어휘집" 스타일). */
object QuickMessages {
    const val ARRIVED = "도착했어요"
    const val ON_MY_WAY = "가는 중이에요"
    const val FIVE_MIN_LATE = "5분 늦어요"
    const val CANT_MAKE_IT = "오늘 참가 어려워요"

    val ALLOWED: Set<String> = setOf(ARRIVED, ON_MY_WAY, FIVE_MIN_LATE, CANT_MAKE_IT)
}

object SocialSessionChatMessageTable : Table("social_session_chat_messages") {
    val id = varchar("id", 32)
    val sessionId = varchar("session_id", 32).references(SocialSessionTable.id)

    // null = 시스템 메시지. 호스트 공지는 senderUserId가 채워진 채 type=HOST_ANNOUNCEMENT.
    val senderUserId = varchar("sender_user_id", 32).references(UserTable.id).nullable()
    val type = enumerationByName("type", 20, ChatMessageType::class)
    val body = text("body")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

/** 신고 대상 종류 — 채팅 메시지 신고(S-35)와 세션 자체 신고(S-32 "[신고]" 액션, 1단계에서 SPEC에
 * 있었지만 안 만들어졌던 gap을 이번에 같이 닫는다) 둘 다 이 범용 테이블 하나로 받는다. */
enum class ContentReportTargetType {
    CHAT_MESSAGE,
    SOCIAL_SESSION,
}

/** content_reports — 자동 조치 전혀 없음(기록만), 모더레이션 큐는 이번 범위 밖. */
object ContentReportTable : Table("content_reports") {
    val id = varchar("id", 32)
    val reporterUserId = varchar("reporter_user_id", 32).references(UserTable.id)
    val targetType = enumerationByName("target_type", 20, ContentReportTargetType::class)
    val targetId = varchar("target_id", 64)
    val reason = text("reason").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}
