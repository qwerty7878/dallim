package com.dallim.app.social.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
import com.dallim.network.social.ChatMessageItem
import com.dallim.network.social.SocialChatMessageType
import com.dallim.network.social.SocialQuickMessages
import com.dallim.network.social.SocialReportRequest
import com.dallim.network.social.SocialSessionApi
import com.dallim.network.social.SocialSessionChatEvent
import com.dallim.network.social.SocialSessionChatSocket
import com.dallim.network.user.BlockUserRequest
import com.dallim.network.user.BlockedUserApi
import com.dallim.network.user.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SocialSessionChatUiState {
    data object Loading : SocialSessionChatUiState

    /** [isAccessExpired] = `SESSION_CHAT_ACCESS_EXPIRED`(시작 +7일) — 재시도 버튼을 숨긴다. */
    data class Error(val message: String, val isAccessExpired: Boolean = false) : SocialSessionChatUiState

    /**
     * @param messages REST 응답과 동일하게 **최신순**(index 0 = 가장 최근) — 화면은
     *   `LazyColumn(reverseLayout = true)`로 그려 최신 메시지가 항상 하단에 오게 한다.
     * @param pendingCancelQuickMessage "오늘 참가 어려워요" 확인 다이얼로그가 열려 있는지.
     * @param reportTargetMessageId 신고 다이얼로그가 특정 메시지를 대상으로 열려 있으면 그 id,
     *   세션 자체 신고([isSessionReportDialogOpen])면 null.
     */
    data class Success(
        val messages: List<ChatMessageItem> = emptyList(),
        val draftText: String = "",
        val pendingCancelQuickMessage: Boolean = false,
        val cancelReasonDraft: String = "",
        val toastMessage: String? = null,
        val reportTargetMessageId: String? = null,
        val isSessionReportDialogOpen: Boolean = false,
        val reportReasonDraft: String = "",
        val isSubmittingReport: Boolean = false,
        /**
         * 2026-09-16 신규(사용자 지시 "호스트가 채팅방을 열 때 공지방이나 모집장소를 가져올 수
         * 있게") — 세션 상세(17.3)의 `meetingPointDetail ?: meetingPointHint`. 새 상태를 만들지
         * 않고 이미 있는 값을 상단에 고정 노출하는 것으로 구현한다. 세션 상세 조회가 실패해도
         * 채팅 자체는 그대로 쓸 수 있어야 하니 null로 남고 카드는 조용히 숨는다.
         */
        val meetingPointText: String? = null,
        /** 롱프레스 액션 메뉴("신고하기"/"이 사용자 차단")가 열려 있는 메시지 id — 2026-09-16
         * 신규(구현 7). 이 메뉴에서 "신고하기"를 고르면 [reportTargetMessageId]로 넘어간다. */
        val actionMenuTargetMessageId: String? = null,
        /** `GET /users/me` 결과 — 자기 자신 메시지에는 액션 메뉴의 "차단" 항목을 숨기는 데만
         * 쓴다. */
        val currentUserId: String? = null,
        /** `GET /users/me/blocks` 결과 — 채팅 화면에서만 발신자를 가려내는 순수 클라이언트
         * 필터(서버는 필터링하지 않음, docs/02-api-spec.md 18.2). */
        val blockedUserIds: Set<String> = emptySet(),
    ) : SocialSessionChatUiState {
        val isReportDialogOpen: Boolean get() = reportTargetMessageId != null || isSessionReportDialogOpen

        /** 최신순으로 오는 [messages]에서 가장 최근 호스트 공지 — 새 API 없이 이미 로드된
         * 메시지 목록에서 계산한다. 없으면 null(고정 카드에서 공지 줄을 생략). */
        val latestAnnouncement: ChatMessageItem?
            get() = messages.firstOrNull { it.type == SocialChatMessageType.HOST_ANNOUNCEMENT }
    }
}

/**
 * S-35 팀 채팅 (docs/02-api-spec.md 17.11/17.12) — REST로 과거 메시지를 한 번 불러온 뒤
 * [SocialSessionChatSocket]으로 실시간 연결한다. 진입 가드(호스트/`APPROVED` 참가자만)는
 * [com.dallim.app.social.detail.SocialSessionDetailScreen]이 버튼 노출 여부로 이미 하지만,
 * 서버가 그래도 403을 줄 수 있어(딥링크 등) 여기서도 그 응답을 그대로 에러 상태로 보여준다.
 */
@HiltViewModel
class SocialSessionChatViewModel @Inject constructor(
    private val socialSessionApi: SocialSessionApi,
    private val chatSocket: SocialSessionChatSocket,
    private val userApi: UserApi,
    private val blockedUserApi: BlockedUserApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: String =
        checkNotNull(savedStateHandle[DallimDestinations.ARG_SOCIAL_SESSION_ID]) { "sessionId 인자가 없습니다." }

    private val _uiState = MutableStateFlow<SocialSessionChatUiState>(SocialSessionChatUiState.Loading)
    val uiState: StateFlow<SocialSessionChatUiState> = _uiState.asStateFlow()

    init {
        load()
        loadCurrentUserId()
        loadBlockedUsers()
    }

    /** 액션 메뉴에서 "차단" 항목을 자기 자신 메시지에 숨기는 데만 쓴다. 실패해도 채팅 자체는
     * 그대로 쓸 수 있어야 하니 조용히 무시한다. */
    private fun loadCurrentUserId() {
        viewModelScope.launch {
            when (val result = safeApiCall { userApi.getMe() }) {
                is UiResult.Success -> updateSuccess { it.copy(currentUserId = result.data.userId) }
                is UiResult.Error -> Unit
                UiResult.Loading -> Unit
            }
        }
    }

    /** 채팅 화면 진입 시 내 차단 목록을 한 번 불러온다(2026-09-16 신규, 구현 7) — 메시지 목록
     * 렌더링에서 발신자가 이 목록에 있으면 본문을 접어서 보여준다. */
    private fun loadBlockedUsers() {
        viewModelScope.launch {
            when (val result = safeApiCall { blockedUserApi.getBlockedUsers() }) {
                is UiResult.Success -> updateSuccess {
                    it.copy(blockedUserIds = result.data.items.mapTo(mutableSetOf()) { item -> item.userId })
                }
                is UiResult.Error -> Unit
                UiResult.Loading -> Unit
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SocialSessionChatUiState.Loading
            when (val result = safeApiCall { socialSessionApi.getChatMessages(sessionId) }) {
                is UiResult.Success -> {
                    _uiState.value = SocialSessionChatUiState.Success(messages = result.data.items)
                    connectSocket()
                    loadMeetingPointInfo()
                }
                is UiResult.Error -> {
                    val isExpired = result.code == "SESSION_CHAT_ACCESS_EXPIRED"
                    _uiState.value = SocialSessionChatUiState.Error(
                        message = if (isExpired) {
                            "채팅이 종료됐어요. 세션 시작 후 7일이 지나면 대화 내용을 볼 수 없어요."
                        } else {
                            result.message
                        },
                        isAccessExpired = isExpired,
                    )
                }
                UiResult.Loading -> Unit
            }
        }
    }

    /** 이미 호스트/`APPROVED` 참가자로서 채팅에 들어온 것이므로 `meetingPointDetail`이 채워져
     * 있을 것이다 — 없으면 항상 채워지는 `meetingPointHint`로 대체한다. 실패해도 채팅 자체를
     * 막지 않는다(고정 카드만 조용히 숨김). */
    private fun loadMeetingPointInfo() {
        viewModelScope.launch {
            when (val result = safeApiCall { socialSessionApi.getSocialSessionDetail(sessionId) }) {
                is UiResult.Success -> {
                    val detail = result.data
                    val text = detail.meetingPointDetail ?: detail.meetingPointHint
                    updateSuccess { it.copy(meetingPointText = text) }
                }
                is UiResult.Error -> Unit
                UiResult.Loading -> Unit
            }
        }
    }

    private fun connectSocket() {
        viewModelScope.launch {
            chatSocket.connect(sessionId).collect { event ->
                when (event) {
                    is SocialSessionChatEvent.MessageReceived -> updateSuccess { state ->
                        if (state.messages.any { it.id == event.message.id }) {
                            state
                        } else {
                            state.copy(messages = listOf(event.message) + state.messages)
                        }
                    }
                    is SocialSessionChatEvent.ErrorReceived -> updateSuccess {
                        it.copy(toastMessage = event.error.message)
                    }
                    is SocialSessionChatEvent.Failed -> updateSuccess {
                        it.copy(toastMessage = "연결이 끊겼어요. 화면을 나갔다가 다시 들어와 주세요.")
                    }
                    is SocialSessionChatEvent.Closed -> Unit
                }
            }
        }
    }

    fun onDraftTextChange(value: String) = updateSuccess { it.copy(draftText = value) }

    fun onSendClick() {
        val state = _uiState.value as? SocialSessionChatUiState.Success ?: return
        val text = state.draftText.trim()
        if (text.isEmpty()) return
        chatSocket.sendText(text)
        updateSuccess { it.copy(draftText = "") }
    }

    /** 고정 4문구 중 [SocialQuickMessages.CANT_MAKE_IT]만 확인 다이얼로그를 거친다(참가 자동
     * 취소 + 온도 감점 가능성이 있어 실수 방지) — 나머지 3개는 바로 전송한다. */
    fun onQuickMessageClick(text: String) {
        if (text == SocialQuickMessages.CANT_MAKE_IT) {
            updateSuccess { it.copy(pendingCancelQuickMessage = true, cancelReasonDraft = "") }
        } else {
            chatSocket.sendQuickMessage(text)
        }
    }

    fun onCancelReasonChange(value: String) = updateSuccess { it.copy(cancelReasonDraft = value) }

    fun onCancelQuickMessageDismiss() = updateSuccess { it.copy(pendingCancelQuickMessage = false) }

    fun onCancelQuickMessageConfirm() {
        val state = _uiState.value as? SocialSessionChatUiState.Success ?: return
        chatSocket.sendQuickMessage(
            SocialQuickMessages.CANT_MAKE_IT,
            cancelReason = state.cancelReasonDraft.trim().ifEmpty { null },
        )
        updateSuccess { it.copy(pendingCancelQuickMessage = false) }
    }

    /** 시스템 메시지(`senderUserId == null`)는 길게 눌러도 액션 메뉴를 열지 않는다(화면에서
     * 애초에 롱프레스를 걸지 않음). 2026-09-16부터 바로 신고 다이얼로그를 열지 않고 "신고하기/
     * 이 사용자 차단" 액션 메뉴를 먼저 연다(구현 7). */
    fun onMessageLongPress(messageId: String) = updateSuccess { it.copy(actionMenuTargetMessageId = messageId) }

    fun onActionMenuDismiss() = updateSuccess { it.copy(actionMenuTargetMessageId = null) }

    fun onReportFromActionMenuClick() {
        val messageId = (_uiState.value as? SocialSessionChatUiState.Success)?.actionMenuTargetMessageId ?: return
        updateSuccess {
            it.copy(actionMenuTargetMessageId = null, reportTargetMessageId = messageId, reportReasonDraft = "")
        }
    }

    /** 채팅 메시지 발신자 차단(docs/02-api-spec.md 18.2, 구현 7) — 성공하면 그 자리에서
     * [SocialSessionChatUiState.Success.blockedUserIds]에 추가해 즉시 그 사람 메시지가 접힌다.
     * 세션 신청/매칭 등 다른 곳에는 아무 영향이 없다(18.3, 서버 스코프 자체가 채팅 한정). */
    fun onBlockUserFromActionMenuClick() {
        val state = _uiState.value as? SocialSessionChatUiState.Success ?: return
        val messageId = state.actionMenuTargetMessageId ?: return
        val targetUserId = state.messages.firstOrNull { it.id == messageId }?.senderUserId ?: return
        updateSuccess { it.copy(actionMenuTargetMessageId = null) }
        viewModelScope.launch {
            when (val result = safeApiCall { blockedUserApi.blockUser(BlockUserRequest(blockedUserId = targetUserId)) }) {
                is UiResult.Success -> updateSuccess {
                    it.copy(blockedUserIds = it.blockedUserIds + targetUserId, toastMessage = "차단했어요.")
                }
                is UiResult.Error -> updateSuccess { it.copy(toastMessage = result.message) }
                UiResult.Loading -> Unit
            }
        }
    }

    fun onSessionReportClick() =
        updateSuccess { it.copy(isSessionReportDialogOpen = true, reportReasonDraft = "") }

    fun onReportDialogDismiss() =
        updateSuccess { it.copy(reportTargetMessageId = null, isSessionReportDialogOpen = false) }

    fun onReportReasonChange(value: String) = updateSuccess { it.copy(reportReasonDraft = value) }

    fun onReportSubmit() {
        val state = _uiState.value as? SocialSessionChatUiState.Success ?: return
        if (state.isSubmittingReport) return
        val messageId = state.reportTargetMessageId
        viewModelScope.launch {
            updateSuccess { it.copy(isSubmittingReport = true) }
            val request = SocialReportRequest(reason = state.reportReasonDraft.trim().ifEmpty { null })
            val result = if (messageId != null) {
                safeApiCall { socialSessionApi.reportChatMessage(sessionId, messageId, request) }
            } else {
                safeApiCall { socialSessionApi.reportSocialSession(sessionId, request) }
            }
            when (result) {
                is UiResult.Success -> updateSuccess {
                    it.copy(
                        isSubmittingReport = false,
                        reportTargetMessageId = null,
                        isSessionReportDialogOpen = false,
                        toastMessage = "신고가 접수됐어요.",
                    )
                }
                is UiResult.Error -> updateSuccess {
                    it.copy(isSubmittingReport = false, toastMessage = result.message)
                }
                UiResult.Loading -> Unit
            }
        }
    }

    fun onToastMessageShown() = updateSuccess { it.copy(toastMessage = null) }

    override fun onCleared() {
        super.onCleared()
        chatSocket.disconnect()
    }

    private inline fun updateSuccess(transform: (SocialSessionChatUiState.Success) -> SocialSessionChatUiState.Success) {
        _uiState.update { state -> if (state is SocialSessionChatUiState.Success) transform(state) else state }
    }
}
