package com.dallim.app.running.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
import com.dallim.network.run.RunApi
import com.dallim.network.run.RunDetailResponseBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RunResultUiState {
    data object Loading : RunResultUiState
    data class Error(val message: String) : RunResultUiState
    data class Success(val run: RunDetailResponseBody) : RunResultUiState
}

/**
 * S-25 달림 결과 (docs/01-feature-spec.md §1.3) — [RunApi.getRun]으로 서버가 최종 계산한 결과를
 * 조회한다. **완주 판정은 서버가 유일한 신뢰 소스**이므로 S-21에서 계산했던 로컬 프리체크값은
 * 여기서 전혀 참조하지 않고, 이 화면은 오직 서버 응답만 그린다(CLAUDE.md rule 3).
 */
@HiltViewModel
class RunResultViewModel @Inject constructor(
    private val runApi: RunApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val runId: String = checkNotNull(savedStateHandle[DallimDestinations.ARG_RUN_ID]) { "runId 인자가 없습니다." }

    private val _uiState = MutableStateFlow<RunResultUiState>(RunResultUiState.Loading)
    val uiState: StateFlow<RunResultUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RunResultUiState.Loading
            _uiState.value = when (val result = safeApiCall { runApi.getRun(runId) }) {
                is UiResult.Success -> RunResultUiState.Success(result.data)
                is UiResult.Error -> RunResultUiState.Error(result.message)
                UiResult.Loading -> RunResultUiState.Loading
            }
        }
    }
}
