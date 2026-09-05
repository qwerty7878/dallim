package com.dallim.app.meetup.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
import com.dallim.network.meetup.MeetupApi
import com.dallim.network.meetup.MeetupListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MeetupListUiState {
    data object Loading : MeetupListUiState
    data class Success(val items: List<MeetupListItem>) : MeetupListUiState
    data class Error(val message: String) : MeetupListUiState
}

/**
 * S-47 모집 목록 — 특정 코스의 모집 게시글 목록 (docs/01-feature-spec.md §1.8, docs/02-api-spec.md
 * 14.2). 인증 불필요 엔드포인트라 비로그인 사용자도 목록은 볼 수 있다(참가/생성은 각 화면에서
 * 로그인이 요구됨 — S-48/S-49는 인증된 라우트에서만 호출된다).
 */
@HiltViewModel
class MeetupListViewModel @Inject constructor(
    private val meetupApi: MeetupApi,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val routeId: String =
        checkNotNull(savedStateHandle[DallimDestinations.ARG_ROUTE_ID]) { "routeId 인자가 없습니다." }

    private val _uiState = MutableStateFlow<MeetupListUiState>(MeetupListUiState.Loading)
    val uiState: StateFlow<MeetupListUiState> = _uiState.asStateFlow()

    init {
        load()

        // S-48에서 모집 생성에 성공하고 돌아오면 NavHost가 이 화면의 back stack entry(=이 화면
        // 자신의 SavedStateHandle)에 결과 플래그를 심어둔다(Compose Navigation 표준 결과 전달
        // 패턴: previousBackStackEntry.savedStateHandle). 여기서 관찰해 목록을 다시 불러온다.
        viewModelScope.launch {
            savedStateHandle.getStateFlow(RESULT_MEETUP_CREATED, false).collect { created ->
                if (created) {
                    savedStateHandle[RESULT_MEETUP_CREATED] = false
                    load()
                }
            }
        }
    }

    /** S-48에서 새 모집을 만들거나 S-49에서 참가/취소하고 돌아왔을 때 목록을 다시 불러온다. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = MeetupListUiState.Loading
            when (val result = safeApiCall { meetupApi.getMeetups(routeId) }) {
                is UiResult.Success -> _uiState.value = MeetupListUiState.Success(result.data.items)
                is UiResult.Error -> _uiState.value = MeetupListUiState.Error(result.message)
                UiResult.Loading -> Unit
            }
        }
    }

    companion object {
        /**
         * S-48(모집 생성) 성공 결과를 이 화면의 back stack entry SavedStateHandle에 심을 때 쓰는 키
         * — NavHost가 `navController.previousBackStackEntry?.savedStateHandle?.set(...)`로 값을
         * 쓰고, 위 [init] 블록이 관찰해 [load]를 다시 호출한다.
         */
        const val RESULT_MEETUP_CREATED = "result_meetup_created"
    }
}
