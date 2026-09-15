package com.dallim.app.social.list

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
 *
 * 세션 생성(S-31) 후 돌아왔을 때의 새로고침은 SavedStateHandle 플래그 릴레이가 아니라
 * [com.dallim.app.discover.DiscoverScreen]이 화면 RESUME마다 [load]를 호출하는 방식으로 처리한다
 * — 이 ViewModel은 탐색(EXPLORE) 탭 루트 안에서 세그먼트에 따라 조건부로 생성되는데, 탭
 * 전환(navigateToTab)의 popUpTo/saveState/restoreState 때문에 NavBackStackEntry의
 * SavedStateHandle 정체성이 이 인스턴스 생성 시점과 달라질 수 있어(실측으로 확인된 버그)
 * SavedStateHandle 플래그 방식이 신뢰할 수 없었다.
 */
@HiltViewModel
class SocialSessionListViewModel @Inject constructor(
    private val socialSessionApi: SocialSessionApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SocialSessionListUiState>(SocialSessionListUiState.Loading)
    val uiState: StateFlow<SocialSessionListUiState> = _uiState.asStateFlow()

    init {
        load()
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
        /** 페이지네이션 없이 한 번에 불러오는 상한 — 1단계 트래픽 규모에서 충분하다. */
        private const val MAX_PAGE_SIZE = 50
    }
}
