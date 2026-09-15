package com.dallim.app.social.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.network.social.SocialSessionApi
import com.dallim.network.social.SocialSessionInboxItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChatInboxUiState {
    data object Loading : ChatInboxUiState
    data class Success(val items: List<SocialSessionInboxItem>) : ChatInboxUiState
    data class Error(val message: String) : ChatInboxUiState
}

/**
 * 채팅 탭 (2026-09-16 신규, v1.3 SPEC 밖 — docs/02-api-spec.md 18.1). 내가 호스트/`APPROVED`
 * 참가자인 소셜 세션 채팅 인박스. 페이지네이션 없이 한 번에 불러온다(한 유저가 동시에 속한
 * 세션 수는 자연히 적다, 서버 문서 근거) — 서버가 이미 정렬해서 내려주므로 클라이언트
 * 재정렬도 하지 않는다. 읽음/안읽음 카운트는 만들지 않는다(18.3, 그 데이터 자체가 없음).
 */
@HiltViewModel
class ChatInboxViewModel @Inject constructor(
    private val socialSessionApi: SocialSessionApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatInboxUiState>(ChatInboxUiState.Loading)
    val uiState: StateFlow<ChatInboxUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ChatInboxUiState.Loading
            when (val result = safeApiCall { socialSessionApi.getSocialSessionInbox() }) {
                is UiResult.Success -> _uiState.value = ChatInboxUiState.Success(result.data.items)
                is UiResult.Error -> _uiState.value = ChatInboxUiState.Error(result.message)
                UiResult.Loading -> Unit
            }
        }
    }
}
