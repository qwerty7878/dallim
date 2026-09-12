package com.dallim.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets

/**
 * S-35 팀 채팅(com.dallim.social.socialSessionChatRoutes)의 `GET /social-sessions/{id}/chat/ws`
 * 하나만 이 플러그인을 쓴다 -- docs/02-api-spec.md 17장 이어서. 서버 프로세스 내 인메모리 커넥션
 * 맵(SocialSessionChatHub)과 짝을 이루며, 실시간 GPS 스트리밍용이 아니다(CLAUDE.md 규칙 4 --
 * GPS는 여전히 POST /runs/{id}/gps-batch로만 받는다).
 */
fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriodMillis = 15_000
        timeoutMillis = 30_000
    }
}
