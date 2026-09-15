package com.dallim.app.social.runningmate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.network.social.RunningMateItem
import com.dallim.network.social.SocialSessionApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RunningMateListUiState {
    data object Loading : RunningMateListUiState
    data class Error(val message: String) : RunningMateListUiState

    data class Success(
        val items: List<RunningMateItem>,
        /** 해제 확인 다이얼로그가 열려 있는 대상(있으면 그 유저 id). */
        val pendingRemoveUserId: String? = null,
        val removingUserId: String? = null,
        val errorMessage: String? = null,
    ) : RunningMateListUiState
}

/**
 * S-39 Running Mate (docs/02-api-spec.md 17.17) — 상호 동의로 성립된 메이트 목록 + 조용한
 * 단방향 해제(상대 알림 없음, 멱등). 마이(S-42)에서 진입한다.
 */
@HiltViewModel
class RunningMateListViewModel @Inject constructor(
    private val socialSessionApi: SocialSessionApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RunningMateListUiState>(RunningMateListUiState.Loading)
    val uiState: StateFlow<RunningMateListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RunningMateListUiState.Loading
            when (val result = safeApiCall { socialSessionApi.getRunningMates() }) {
                is UiResult.Success -> _uiState.value = RunningMateListUiState.Success(items = result.data.items)
                is UiResult.Error -> _uiState.value = RunningMateListUiState.Error(result.message)
                UiResult.Loading -> Unit
            }
        }
    }

    fun onRemoveClick(userId: String) = updateSuccess { it.copy(pendingRemoveUserId = userId) }

    fun onRemoveDialogDismiss() = updateSuccess { it.copy(pendingRemoveUserId = null) }

    fun onRemoveConfirm() {
        val state = _uiState.value as? RunningMateListUiState.Success ?: return
        val userId = state.pendingRemoveUserId ?: return
        viewModelScope.launch {
            updateSuccess { it.copy(pendingRemoveUserId = null, removingUserId = userId) }
            when (val result = safeApiCall { socialSessionApi.deleteRunningMate(userId) }) {
                is UiResult.Success -> updateSuccess {
                    it.copy(removingUserId = null, items = it.items.filterNot { item -> item.userId == userId })
                }
                is UiResult.Error -> updateSuccess { it.copy(removingUserId = null, errorMessage = result.message) }
                UiResult.Loading -> Unit
            }
        }
    }

    fun onErrorMessageShown() = updateSuccess { it.copy(errorMessage = null) }

    private inline fun updateSuccess(transform: (RunningMateListUiState.Success) -> RunningMateListUiState.Success) {
        _uiState.update { state -> if (state is RunningMateListUiState.Success) transform(state) else state }
    }
}
