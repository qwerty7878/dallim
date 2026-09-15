package com.dallim.app.social.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.network.social.SocialSessionApi
import com.dallim.network.social.SocialSessionListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SocialSessionListUiState {
    data object Loading : SocialSessionListUiState
    data class Success(val items: List<SocialSessionListItem>) : SocialSessionListUiState
    data class Error(val message: String) : SocialSessionListUiState
}

/**
 * S-30 세션 탐색 (docs/02-api-spec.md 17.1). 인증 불필요 엔드포인트라 비로그인 사용자도 목록은
 * 볼 수 있다(참가/생성은 각 화면에서 로그인이 요구됨). 지도 핀 토글/지역 거점 제한/세밀한 필터는
 * 이번 1단계 범위 밖(과설계 금지, 작업 브리핑 참고) — 페이지네이션도 없이 한 번에 불러온다
 * ([com.dallim.app.meetup.list.MeetupListViewModel]과 동일한 단순함 원칙).
 */
@HiltViewModel
class SocialSessionListViewModel @Inject constructor(
    private val socialSessionApi: SocialSessionApi,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SocialSessionListUiState>(SocialSessionListUiState.Loading)
    val uiState: StateFlow<SocialSessionListUiState> = _uiState.asStateFlow()

    init {
        load()

        // S-31에서 세션 생성에 성공하고 돌아오면 목록을 다시 불러온다
        // (com.dallim.app.meetup.list.MeetupListViewModel의 RESULT_MEETUP_CREATED와 동일 패턴).
        viewModelScope.launch {
            savedStateHandle.getStateFlow(RESULT_SESSION_CREATED, false).collect { created ->
                if (created) {
                    savedStateHandle[RESULT_SESSION_CREATED] = false
                    load()
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SocialSessionListUiState.Loading
            when (val result = safeApiCall { socialSessionApi.getSocialSessions(size = MAX_PAGE_SIZE) }) {
                is UiResult.Success -> _uiState.value = SocialSessionListUiState.Success(result.data.items)
                is UiResult.Error -> _uiState.value = SocialSessionListUiState.Error(result.message)
                UiResult.Loading -> Unit
            }
        }
    }

    companion object {
        const val RESULT_SESSION_CREATED = "result_social_session_created"

        /** 페이지네이션 없이 한 번에 불러오는 상한 — 1단계 트래픽 규모에서 충분하다. */
        private const val MAX_PAGE_SIZE = 50
    }
}
