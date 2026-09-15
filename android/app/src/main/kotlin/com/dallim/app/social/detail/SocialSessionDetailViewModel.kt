package com.dallim.app.social.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
import com.dallim.network.social.ApplySocialSessionRequest
import com.dallim.network.social.SocialSessionApi
import com.dallim.network.social.SocialSessionDetailResponseBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SocialSessionDetailUiState {
    data object Loading : SocialSessionDetailUiState
    data class Error(val message: String) : SocialSessionDetailUiState

    /**
     * @param isActionInProgress 신청 취소 요청이 진행 중인지.
     * @param actionErrorMessage 신청 취소 실패 시 서버 메시지.
     * @param isApplyDialogOpen S-33 참가 신청 다이얼로그가 열려 있는지 — 별도 화면이 아니라
     *   이 상세 화면 위에 다이얼로그로만 존재한다(작업 브리핑 지시).
     * @param applyMessage S-33에서 입력 중인 한 줄 메시지(선택).
     * @param isApplying S-33 [신청하기] 요청이 진행 중인지.
     * @param applyErrorMessage S-33 신청 실패 시 서버 메시지(예: `SESSION_CONDITION_NOT_MET` ->
     *   "참가 조건이 맞지 않아요.", 이미 서버가 통일된 문구를 주므로 그대로 노출한다).
     */
    data class Success(
        val detail: SocialSessionDetailResponseBody,
        val isActionInProgress: Boolean = false,
        val actionErrorMessage: String? = null,
        val isApplyDialogOpen: Boolean = false,
        val applyMessage: String = "",
        val isApplying: Boolean = false,
        val applyErrorMessage: String? = null,
    ) : SocialSessionDetailUiState
}

/**
 * S-32 세션 상세 (docs/02-api-spec.md 17.4). [com.dallim.app.meetup.detail.MeetupDetailViewModel]
 * 과 동일한 상태 홀더 패턴. [DallimDestinations.SOCIAL_SESSION_APPLICANTS](S-34)에서 승인 처리를
 * 하고 돌아오면 `approvedCount`가 바뀌었을 수 있으니, NavHost가 그 백스택 엔트리에 심는
 * [RESULT_SOCIAL_SESSION_UPDATED] 플래그를 관찰해 다시 불러온다.
 */
@HiltViewModel
class SocialSessionDetailViewModel @Inject constructor(
    private val socialSessionApi: SocialSessionApi,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: String =
        checkNotNull(savedStateHandle[DallimDestinations.ARG_SOCIAL_SESSION_ID]) { "sessionId 인자가 없습니다." }

    private val _uiState = MutableStateFlow<SocialSessionDetailUiState>(SocialSessionDetailUiState.Loading)
    val uiState: StateFlow<SocialSessionDetailUiState> = _uiState.asStateFlow()

    init {
        load()

        viewModelScope.launch {
            savedStateHandle.getStateFlow(RESULT_SOCIAL_SESSION_UPDATED, false).collect { updated ->
                if (updated) {
                    savedStateHandle[RESULT_SOCIAL_SESSION_UPDATED] = false
                    load()
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SocialSessionDetailUiState.Loading
            when (val result = safeApiCall { socialSessionApi.getSocialSessionDetail(sessionId) }) {
                is UiResult.Success -> _uiState.value = SocialSessionDetailUiState.Success(detail = result.data)
                is UiResult.Error -> _uiState.value = SocialSessionDetailUiState.Error(result.message)
                UiResult.Loading -> Unit
            }
        }
    }

    fun onApplyClick() {
        val state = _uiState.value
        if (state !is SocialSessionDetailUiState.Success) return
        _uiState.value = state.copy(isApplyDialogOpen = true, applyMessage = "", applyErrorMessage = null)
    }

    fun onApplyDialogDismiss() {
        val state = _uiState.value
        if (state !is SocialSessionDetailUiState.Success) return
        _uiState.value = state.copy(isApplyDialogOpen = false)
    }

    fun onApplyMessageChange(value: String) {
        val state = _uiState.value
        if (state !is SocialSessionDetailUiState.Success) return
        _uiState.value = state.copy(applyMessage = value)
    }

    /** S-33 [신청하기] — 성공하면 다이얼로그를 닫고 상세를 다시 불러와 `myApplicationStatus`가
     * "PENDING"으로 바뀐 화면을 보여준다. */
    fun onApplySubmit() {
        val state = _uiState.value
        if (state !is SocialSessionDetailUiState.Success || state.isApplying) return

        viewModelScope.launch {
            _uiState.value = state.copy(isApplying = true, applyErrorMessage = null)
            val request = ApplySocialSessionRequest(message = state.applyMessage.trim().ifEmpty { null })
            when (val result = safeApiCall { socialSessionApi.applySocialSession(sessionId, request) }) {
                is UiResult.Success -> load()
                is UiResult.Error -> {
                    _uiState.value = (_uiState.value as? SocialSessionDetailUiState.Success)
                        ?.copy(isApplying = false, applyErrorMessage = result.message)
                        ?: _uiState.value
                }
                UiResult.Loading -> Unit
            }
        }
    }

    /** PENDING 상태에서만 신청 취소가 가능하다(17.5) — resolveAction이 이 상태에서만 버튼을 보여준다. */
    fun onCancelApplyClick() {
        val state = _uiState.value
        if (state !is SocialSessionDetailUiState.Success || state.isActionInProgress) return

        viewModelScope.launch {
            _uiState.value = state.copy(isActionInProgress = true, actionErrorMessage = null)
            when (val result = safeApiCall { socialSessionApi.cancelSocialSessionApply(sessionId) }) {
                is UiResult.Success -> load()
                is UiResult.Error -> {
                    _uiState.value = (_uiState.value as? SocialSessionDetailUiState.Success)
                        ?.copy(isActionInProgress = false, actionErrorMessage = result.message)
                        ?: _uiState.value
                }
                UiResult.Loading -> Unit
            }
        }
    }

    companion object {
        const val RESULT_SOCIAL_SESSION_UPDATED = "result_social_session_updated"
    }
}
