package com.dallim.network.social

import com.dallim.network.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject

/** WebSocket -> ViewModel 이벤트 — S-35 팀 채팅. */
sealed interface SocialSessionChatEvent {
    data class MessageReceived(val message: ChatMessageItem) : SocialSessionChatEvent
    data class ErrorReceived(val error: SocialChatErrorFrame) : SocialSessionChatEvent
    data class Closed(val code: Int, val reason: String) : SocialSessionChatEvent
    data class Failed(val message: String) : SocialSessionChatEvent
}

/** 보낸 사람 한 명에게만 오는 에러 프레임(docs/02-api-spec.md 17.12) — 브로드캐스트되지 않는다. */
@Serializable
data class SocialChatErrorFrame(val code: String, val message: String)

/** 클라이언트 -> 서버 텍스트 프레임(17.12). `type`은 [SocialChatMessageType.TEXT] 또는
 * [SocialChatMessageType.QUICK_MESSAGE]만 보낼 수 있다(HOST_ANNOUNCEMENT/SYSTEM은 서버 전용). */
@Serializable
private data class OutgoingChatFrame(
    val type: String,
    val body: String,
    val cancelReason: String? = null,
)

/**
 * S-35 팀 채팅 WebSocket(`GET /social-sessions/{sessionId}/chat/ws`, docs/02-api-spec.md 17.12).
 * Ktor 네이티브 WebSocket이라 REST와 별도 클라이언트가 필요하지만, **새 OkHttpClient는 만들지
 * 않는다** — 기존 싱글턴 [OkHttpClient](AuthInterceptor 포함, [com.dallim.network.di.NetworkModule])
 * 를 그대로 주입받아 `newWebSocket`을 호출한다. OkHttp는 애플리케이션 인터셉터를 WebSocket
 * 업그레이드 요청에도 그대로 적용하므로 `Authorization` 헤더가 자동으로 실린다(작업 브리핑에서
 * 미리 확인하라고 한 부분).
 *
 * [BuildConfig.BASE_URL]의 http(s) 스킴만 ws(s)로 바꿔 URL을 구성한다 — REST와 완전히 같은
 * 호스트/포트/`/v1/` 프리픽스를 그대로 재사용.
 *
 * `@Inject constructor`이지만 `@Singleton`이 아니다 — [com.dallim.app.social.chat
 * .SocialSessionChatViewModel]마다(=화면마다) Hilt가 새 인스턴스를 만들어줘, 화면을 나갈 때
 * [disconnect]만 부르면 이전 연결이 다른 화면에 남는 일이 없다.
 */
class SocialSessionChatSocket @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var activeWebSocket: WebSocket? = null

    /**
     * 연결을 열고 들어오는 프레임을 [SocialSessionChatEvent]로 변환해 흘려보낸다. Flow가
     * 취소되면(화면 이탈/[disconnect]) 소켓도 함께 정상 종료(code 1000)된다.
     */
    fun connect(sessionId: String): Flow<SocialSessionChatEvent> = callbackFlow {
        val wsBaseUrl = BuildConfig.BASE_URL.replaceFirst("http", "ws")
        val request = Request.Builder()
            .url("${wsBaseUrl}social-sessions/$sessionId/chat/ws")
            .build()

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { json.decodeFromString(ChatMessageItem.serializer(), text) }.getOrNull()
                if (message != null) {
                    trySend(SocialSessionChatEvent.MessageReceived(message))
                    return
                }
                val error = runCatching { json.decodeFromString(SocialChatErrorFrame.serializer(), text) }.getOrNull()
                if (error != null) {
                    trySend(SocialSessionChatEvent.ErrorReceived(error))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                trySend(SocialSessionChatEvent.Closed(code, reason))
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(SocialSessionChatEvent.Failed(t.message ?: "채팅 연결에 실패했어요."))
                close()
            }
        }

        activeWebSocket = okHttpClient.newWebSocket(request, listener)

        awaitClose {
            activeWebSocket?.close(NORMAL_CLOSURE_CODE, null)
            activeWebSocket = null
        }
    }

    /** [SocialChatMessageType.TEXT] 전송 — 내가 호스트면 서버가 자동으로 HOST_ANNOUNCEMENT로
     * 승격해 브로드캐스트한다(클라이언트가 신경 쓸 필요 없음). */
    fun sendText(body: String): Boolean = send(OutgoingChatFrame(type = SocialChatMessageType.TEXT, body = body))

    /** [SocialQuickMessages] 고정 4문구 중 하나만 보낼 수 있다. [SocialQuickMessages.CANT_MAKE_IT]
     * 는 [cancelReason]을 함께 실어 보낼 수 있다(참가 자동 취소 + 사유 시스템 메시지). */
    fun sendQuickMessage(body: String, cancelReason: String? = null): Boolean =
        send(OutgoingChatFrame(type = SocialChatMessageType.QUICK_MESSAGE, body = body, cancelReason = cancelReason))

    /** 화면 이탈 시 명시적으로 호출(ViewModel.onCleared) — Flow 취소로도 정리되지만 즉시 끊고
     * 싶을 때를 위해 별도로 노출한다. */
    fun disconnect() {
        activeWebSocket?.close(NORMAL_CLOSURE_CODE, null)
        activeWebSocket = null
    }

    private fun send(frame: OutgoingChatFrame): Boolean =
        activeWebSocket?.send(json.encodeToString(OutgoingChatFrame.serializer(), frame)) ?: false

    private companion object {
        const val NORMAL_CLOSURE_CODE = 1000
    }
}
