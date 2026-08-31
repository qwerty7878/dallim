package com.dallim.app.running.share

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ShareCardUiState {
    data object Loading : ShareCardUiState
    data class Error(val message: String) : ShareCardUiState
    data class Ready(val run: RunDetailResponseBody, val options: ShareCardOptions = ShareCardOptions()) : ShareCardUiState
}

/** S-26 공유 카드 편집 — 배경/비율/표시항목 옵션 상태만 들고 있고, 실제 Canvas->Bitmap 합성은 [ShareCardRenderer]가 한다. */
@HiltViewModel
class ShareCardViewModel @Inject constructor(
    private val runApi: RunApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val runId: String = checkNotNull(savedStateHandle[DallimDestinations.ARG_RUN_ID]) { "runId 인자가 없습니다." }

    private val _uiState = MutableStateFlow<ShareCardUiState>(ShareCardUiState.Loading)
    val uiState: StateFlow<ShareCardUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ShareCardUiState.Loading
            _uiState.value = when (val result = safeApiCall { runApi.getRun(runId) }) {
                is UiResult.Success -> ShareCardUiState.Ready(run = result.data)
                is UiResult.Error -> ShareCardUiState.Error(result.message)
                UiResult.Loading -> ShareCardUiState.Loading
            }
        }
    }

    fun onBackgroundSelected(background: ShareCardBackground) = updateOptions { it.copy(background = background) }
    fun onRatioSelected(ratio: ShareCardRatio) = updateOptions { it.copy(ratio = ratio) }
    fun onToggleDistance() = updateOptions { it.copy(showDistance = !it.showDistance) }
    fun onToggleDuration() = updateOptions { it.copy(showDuration = !it.showDuration) }
    fun onTogglePace() = updateOptions { it.copy(showPace = !it.showPace) }

    private inline fun updateOptions(transform: (ShareCardOptions) -> ShareCardOptions) {
        _uiState.update { state -> if (state is ShareCardUiState.Ready) state.copy(options = transform(state.options)) else state }
    }
}
