package com.dallim.app.social.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
import com.dallim.network.social.SocialSessionApi
import com.dallim.network.social.SocialSessionApplicantItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SocialSessionApplicantsUiState {
    data object Loading : SocialSessionApplicantsUiState
    data class Error(val message: String) : SocialSessionApplicantsUiState

    /**
     * @param approvingUserId 승인 요청이 진행 중인 신청자의 userId(동시에 하나만 승인 가능,
     *   버튼 중복 클릭 방지).
     * @param actionErrorMessage 승인 실패 시 서버 메시지(예: 정원초과 409 -> "정원이 가득 찼어요.").
     * @param didUpdate 이 화면에서 한 번이라도 승인에 성공했는지 — 뒤로가기 시 S-32 상세를
     *   새로고침해야 하는지 판단하는 데 쓰인다.
     */
    data class Success(
        val items: List<SocialSessionApplicantItem>,
        val approvingUserId: String? = null,
        val actionErrorMessage: String? = null,
        val didUpdate: Boolean = false,
    ) : SocialSessionApplicantsUiState
}

/**
 * S-34 호스트 — 신청자 관리 (docs/02-api-spec.md 17.6/17.7). 호스트가 아니면 서버가 403을 준다 —
 * 이 화면은 항상 S-32(세션 상세)의 "신청자 관리" 버튼(호스트에게만 보임)을 거쳐 들어오므로 별도
 * 방어는 하지 않는다.
 */
@HiltViewModel
class SocialSessionApplicantsViewModel @Inject constructor(
    private val socialSessionApi: SocialSessionApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: String =
        checkNotNull(savedStateHandle[DallimDestinations.ARG_SOCIAL_SESSION_ID]) { "sessionId 인자가 없습니다." }

    private val _uiState = MutableStateFlow<SocialSessionApplicantsUiState>(SocialSessionApplicantsUiState.Loading)
    val uiState: StateFlow<SocialSessionApplicantsUiState> = _uiState.asStateFlow()

    /** NavHost가 뒤로가기 시 S-32 새로고침 여부를 판단하는 데 쓴다(SocialSessionDetailViewModel
     * .RESULT_SOCIAL_SESSION_UPDATED 참고). */
    val didUpdate: Boolean
        get() = (_uiState.value as? SocialSessionApplicantsUiState.Success)?.didUpdate == true

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SocialSessionApplicantsUiState.Loading
            when (val result = safeApiCall { socialSessionApi.getSocialSessionApplicants(sessionId) }) {
                is UiResult.Success -> _uiState.value = SocialSessionApplicantsUiState.Success(items = result.data.items)
                is UiResult.Error -> _uiState.value = SocialSessionApplicantsUiState.Error(result.message)
                UiResult.Loading -> Unit
            }
        }
    }

    fun onApproveClick(userId: String) {
        val state = _uiState.value
        if (state !is SocialSessionApplicantsUiState.Success || state.approvingUserId != null) return

        viewModelScope.launch {
            _uiState.value = state.copy(approvingUserId = userId, actionErrorMessage = null)
            when (val result = safeApiCall { socialSessionApi.approveSocialSessionApplicant(sessionId, userId) }) {
                is UiResult.Success -> {
                    // 목록을 다시 불러와 상태(PENDING -> APPROVED)를 반영하고, didUpdate를 유지한다.
                    when (val reload = safeApiCall { socialSessionApi.getSocialSessionApplicants(sessionId) }) {
                        is UiResult.Success -> _uiState.value =
                            SocialSessionApplicantsUiState.Success(items = reload.data.items, didUpdate = true)
                        is UiResult.Error -> _uiState.value = SocialSessionApplicantsUiState.Error(reload.message)
                        UiResult.Loading -> Unit
                    }
                }
                is UiResult.Error -> {
                    _uiState.value = (_uiState.value as? SocialSessionApplicantsUiState.Success)
                        ?.copy(approvingUserId = null, actionErrorMessage = result.message)
                        ?: _uiState.value
                }
                UiResult.Loading -> Unit
            }
        }
    }
}
