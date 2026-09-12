package com.dallim.social

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * 세션별 WebSocket 커넥션 집합을 서버 프로세스 메모리에 들고 있는 브로드캐스트 허브(S-35) —
 * EC2 단일 인스턴스라 인스턴스 간 fan-out이 필요 없다(작업 브리핑 참고). 인스턴스가 여러 대가
 * 되면 이 클래스 내부만 Redis Pub/Sub로 바꾸면 되고 호출부(SocialSessionChatRoutes)는 그대로
 * 두면 되는 구조이되, 지금 그 추상화를 미리 만들지는 않는다(과설계 금지).
 *
 * Koin single로 등록되어 앱 전체에서 하나만 존재한다(com.dallim.social.SocialSessionChatModule).
 */
class SocialSessionChatHub {
    private val json = Json { encodeDefaults = true }
    private val connectionsBySession = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    fun register(sessionId: String, connection: DefaultWebSocketServerSession) {
        connectionsBySession.computeIfAbsent(sessionId) { ConcurrentHashMap.newKeySet() }.add(connection)
    }

    fun unregister(sessionId: String, connection: DefaultWebSocketServerSession) {
        connectionsBySession[sessionId]?.remove(connection)
    }

    /** 같은 세션에 연결된 모든 커넥션(보낸 사람 포함)에게 브로드캐스트한다 -- 보낸 사람 클라이언트도
     * 서버가 확정한 id/createdAt이 찍힌 메시지를 그대로 다시 받아 렌더링하는 게 낙관적 UI 없이도
     * 단순하다(과설계 금지). 끊긴 커넥션으로의 전송 실패는 조용히 무시(다음 unregister에서 정리). */
    suspend fun broadcast(sessionId: String, message: ChatMessageItem) {
        val connections = connectionsBySession[sessionId] ?: return
        val payload = json.encodeToString(ChatMessageItem.serializer(), message)
        for (connection in connections.toList()) {
            runCatching { connection.send(Frame.Text(payload)) }
        }
    }
}
