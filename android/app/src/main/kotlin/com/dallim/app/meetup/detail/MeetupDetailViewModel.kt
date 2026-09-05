package com.dallim.app.meetup.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
import com.dallim.network.common.ApiResponse
import com.dallim.network.meetup.MEETUP_STATUS_CANCELLED
import com.dallim.network.meetup.MeetupApi
import com.dallim.network.meetup.MeetupDetailResponseBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import javax.inject.Inject

sealed interface MeetupDetailUiState {
    data object Loading : MeetupDetailUiState
    data class Error(val message: String) : MeetupDetailUiState

    /**
     * @param isActionInProgress join/leave/cancel 요청이 진행 중이라 액션 버튼을 비활성화해야 하는지.
     * @param actionErrorMessage join/leave/cancel 실패 시 서버 메시지(14.4 각 에러 코드에 이미 적절한
     *   한국어 메시지가 달려 있어 — `MEETUP_FULL` -> "정원이 가득 찼어요." 등 — 코드별로 클라이언트가
     *   문구를 다시 만들지 않고 `safeApiCall`이 돌려주는 서버 메시지를 그대로 노출한다. S-48
     *   `MeetupCreateViewModel`과 동일한 관례).
     * @param showCancelConfirm 모집 취소(DELETE)는 되돌릴 수 없는 액션이라 확인 다이얼로그를 거친다.
     */
    data class Success(
        val detail: MeetupDetailResponseBody,
        val isActionInProgress: Boolean = false,
        val actionErrorMessage: String? = null,
        val showCancelConfirm: Boolean = false,
    ) : MeetupDetailUiState
}

sealed interface MeetupDetailNavigationEvent {
    /** 모집 취소(DELETE) 성공 — S-47로 돌아가면서 목록을 새로고침해야 함을 알리는 신호. */
    data object Cancelled : MeetupDetailNavigationEvent
}

/**
 * S-49 모집 상세 (docs/01-feature-spec.md §1.8, docs/02-api-spec.md 14.4).
 * [MeetupListViewModel]/[MeetupCreateViewModel]과 동일한 상태 홀더 패턴을 따른다.
 */
@HiltViewModel
class MeetupDetailViewModel @Inject constructor(
    private val meetupApi: MeetupApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val meetupId: String =
        checkNotNull(savedStateHandle[DallimDestinations.ARG_MEETUP_ID]) { "meetupId 인자가 없습니다." }

    private val _uiState = MutableStateFlow<MeetupDetailUiState>(MeetupDetailUiState.Loading)
    val uiState: StateFlow<MeetupDetailUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<MeetupDetailNavigationEvent>()
    val navigationEvents: SharedFlow<MeetupDetailNavigationEvent> = _navigationEvents.asSharedFlow()

    init {
        load()
    }

    /** 최초 진입 시, 그리고 join/leave 성공 후 최신 상태(참가 인원/isJoined 등)를 다시 불러온다. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = MeetupDetailUiState.Loading
            when (val result = safeApiCall { meetupApi.getMeetupDetail(meetupId) }) {
                is UiResult.Success -> _uiState.value = MeetupDetailUiState.Success(detail = result.data)
                is UiResult.Error -> _uiState.value = MeetupDetailUiState.Error(result.message)
                UiResult.Loading -> Unit
            }
        }
    }

    fun onJoinClick() = runAction { meetupApi.joinMeetup(meetupId) }

    fun onLeaveClick() = runAction { meetupApi.leaveMeetup(meetupId) }

    /** [DELETE]는 되돌릴 수 없으므로 확인 다이얼로그를 먼저 띄운다 — [onCancelConfirm]에서 실제 호출. */
    fun onCancelClick() {
        val state = _uiState.value
        if (state !is MeetupDetailUiState.Success) return
        _uiState.value = state.copy(showCancelConfirm = true)
    }

    fun onCancelDismiss() {
        val state = _uiState.value
        if (state !is MeetupDetailUiState.Success) return
        _uiState.value = state.copy(showCancelConfirm = false)
    }

    fun onCancelConfirm() {
        val state = _uiState.value
        if (state !is MeetupDetailUiState.Success) return
        _uiState.value = state.copy(showCancelConfirm = false)

        viewModelScope.launch {
            _uiState.value = (_uiState.value as? MeetupDetailUiState.Success)
                ?.copy(isActionInProgress = true, actionErrorMessage = null)
                ?: return@launch
            when (val result = safeApiCall { meetupApi.cancelMeetup(meetupId) }) {
                is UiResult.Success -> _navigationEvents.emit(MeetupDetailNavigationEvent.Cancelled)
                is UiResult.Error -> {
                    _uiState.value = (_uiState.value as? MeetupDetailUiState.Success)
                        ?.copy(isActionInProgress = false, actionErrorMessage = result.message)
                        ?: _uiState.value
                }
                UiResult.Loading -> Unit
            }
        }
    }

    /** [onJoinClick]/[onLeaveClick] 공통 처리: 진행 중 표시 -> 호출 -> 성공 시 [load]로 최신 상태 반영. */
    private fun runAction(block: suspend () -> Response<ApiResponse<Unit>>) {
        val state = _uiState.value
        if (state !is MeetupDetailUiState.Success || state.isActionInProgress) return

        viewModelScope.launch {
            _uiState.value = state.copy(isActionInProgress = true, actionErrorMessage = null)
            when (val result = safeApiCall(block)) {
                is UiResult.Success -> load()
                is UiResult.Error -> {
                    _uiState.value = (_uiState.value as? MeetupDetailUiState.Success)
                        ?.copy(isActionInProgress = false, actionErrorMessage = result.message)
                        ?: _uiState.value
                }
                UiResult.Loading -> Unit
            }
        }
    }
}

/** 모집이 취소된 상태인지 — 참가자 여부와 무관하게 액션 버튼을 숨기는 데 쓰인다. */
internal fun MeetupDetailResponseBody.isCancelled(): Boolean = status == MEETUP_STATUS_CANCELLED
