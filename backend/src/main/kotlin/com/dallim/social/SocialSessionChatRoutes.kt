package com.dallim.social

import com.dallim.common.ApiException
import com.dallim.common.ApiResponse
import com.dallim.common.ErrorCodes
import com.dallim.plugins.AUTH_JWT
import com.dallim.plugins.currentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

/**
 * S-35 팀 채팅 엔드포인트 — docs/02-api-spec.md 17장 이어서. WebSocket 핸드셰이크(HTTP GET
 * Upgrade 요청)에도 `authenticate(AUTH_JWT)`가 그대로 적용된다 -- Authorization 헤더가 핸드셰이크
 * 요청 자체에 실려오기 때문(작업 브리핑에서 미리 확인하라고 한 부분, 실제로 별다른 특수 처리
 * 없이 동작한다).
 */
fun Route.socialSessionChatRoutes() {
    val chatService by inject<SocialSessionChatService>()
    val chatHub by inject<SocialSessionChatHub>()
    val json = Json { ignoreUnknownKeys = true }

    route("/social-sessions/{sessionId}") {
        authenticate(AUTH_JWT) {
            get("/chat/messages") {
                val sessionId = call.parameters["sessionId"]!!
                val userId = call.currentUserId()!!
                val q = call.request.queryParameters
                val response = chatService.listMessages(
                    sessionId = sessionId,
                    userId = userId,
                    page = q["page"]?.toIntOrNull() ?: 0,
                    size = q["size"]?.toIntOrNull() ?: 30,
                )
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }

            post("/chat/messages/{messageId}/report") {
                val sessionId = call.parameters["sessionId"]!!
                val messageId = call.parameters["messageId"]!!
                val userId = call.currentUserId()!!
                val request = call.receive<ReportRequest>()
                chatService.reportMessage(sessionId, messageId, userId, request.reason)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }

            post("/report") {
                val sessionId = call.parameters["sessionId"]!!
                val userId = call.currentUserId()!!
                val request = call.receive<ReportRequest>()
                chatService.reportSession(sessionId, userId, request.reason)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }

            webSocket("/chat/ws") {
                val sessionId = call.parameters["sessionId"]!!
                val userId = call.currentUserId()!!

                val session = try {
                    chatService.requireAccess(sessionId, userId).also { chatService.assertReadable(it) }
                } catch (e: ApiException) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, e.message))
                    return@webSocket
                }

                chatHub.register(sessionId, this)
                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val incomingFrame = try {
                            json.decodeFromString(IncomingChatFrame.serializer(), frame.readText())
                        } catch (e: SerializationException) {
                            send(Frame.Text(json.encodeToString(ChatErrorFrame.serializer(), ChatErrorFrame(ErrorCodes.VALIDATION_ERROR, "메시지 형식이 올바르지 않아요."))))
                            continue
                        }
                        try {
                            chatService.handleIncoming(sessionId, session, userId, incomingFrame)
                        } catch (e: ApiException) {
                            send(Frame.Text(json.encodeToString(ChatErrorFrame.serializer(), ChatErrorFrame(e.code, e.message))))
                        }
                    }
                } finally {
                    chatHub.unregister(sessionId, this)
                }
            }
        }
    }
}
