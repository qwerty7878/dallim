package com.dallim.app.dallimbook.detail

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

sealed interface DallimbookDetailUiState {
    data object Loading : DallimbookDetailUiState
    data class Error(val message: String) : DallimbookDetailUiState
    data class Success(val run: RunDetailResponseBody) : DallimbookDetailUiState
}

/**
 * S-41 작품 상세 (docs/01-feature-spec.md §1.4) — [RunApi.getRun]으로 개별 Run 상세를 조회한다.
 * S-25([com.dallim.app.running.result.RunResultViewModel])와 데이터 소스가 완전히 동일하다
 * (같은 `GET /runs/{id}` 응답 구조) — 달림북 그리드에서 지난 작품을 다시 열어보는 진입점이라는
 * 점만 다르다. 거리/시간/페이스/Match 등은 전부 서버 응답값을 그대로 표시한다(CLAUDE.md rule 3).
 */
@HiltViewModel
class DallimbookDetailViewModel @Inject constructor(
    private val runApi: RunApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val runId: String = checkNotNull(savedStateHandle[DallimDestinations.ARG_RUN_ID]) { "runId 인자가 없습니다." }

    private val _uiState = MutableStateFlow<DallimbookDetailUiState>(DallimbookDetailUiState.Loading)
    val uiState: StateFlow<DallimbookDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = DallimbookDetailUiState.Loading
            _uiState.value = when (val result = safeApiCall { runApi.getRun(runId) }) {
                is UiResult.Success -> DallimbookDetailUiState.Success(result.data)
                is UiResult.Error -> DallimbookDetailUiState.Error(result.message)
                UiResult.Loading -> DallimbookDetailUiState.Loading
            }
        }
    }
}
