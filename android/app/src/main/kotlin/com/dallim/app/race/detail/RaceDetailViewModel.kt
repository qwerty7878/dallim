package com.dallim.app.race.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
import com.dallim.network.race.RaceApi
import com.dallim.network.race.RaceDetailResponseBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RaceDetailUiState {
    data object Loading : RaceDetailUiState

    data class Success(
        val race: RaceDetailResponseBody,
        val isSaving: Boolean = false,
        val saveErrorMessage: String? = null,
    ) : RaceDetailUiState

    data class Error(val message: String) : RaceDetailUiState
}

/**
 * S-81 대회 상세 — 종목별 표 + 담기 토글 (docs/02-api-spec.md 16장, docs/달림_화면별_상세기획서_v1.3.md
 * PART 3-H). 접수하러 가기 외부 링크/목표 D-day 카드/진행률/"이 대회 준비하는 사람들"/신고 기능은
 * 16.6에 범위 밖으로 명시돼 있어 만들지 않는다.
 */
@HiltViewModel
class RaceDetailViewModel @Inject constructor(
    private val raceApi: RaceApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val raceId: String =
        checkNotNull(savedStateHandle[DallimDestinations.ARG_RACE_ID]) { "raceId 인자가 없습니다." }

    private val _uiState = MutableStateFlow<RaceDetailUiState>(RaceDetailUiState.Loading)
    val uiState: StateFlow<RaceDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RaceDetailUiState.Loading
            _uiState.value = when (val result = safeApiCall { raceApi.getRaceDetail(raceId) }) {
                is UiResult.Success -> RaceDetailUiState.Success(race = result.data)
                is UiResult.Error -> RaceDetailUiState.Error(result.message)
                UiResult.Loading -> RaceDetailUiState.Loading
            }
        }
    }

    /**
     * 담기/담기취소 토글 — 낙관적 업데이트 후 실패 시 원복
     * (RaceListViewModel.onToggleSaveClick과 동일 패턴). `savedCount`도 함께 +-1 한다.
     */
    fun onToggleSaveClick() {
        val current = _uiState.value as? RaceDetailUiState.Success ?: return
        if (current.isSaving) return

        val wasSaved = current.race.isSaved
        val nextSaved = !wasSaved
        val savedCountDelta = if (nextSaved) 1 else -1

        _uiState.value = current.copy(
            race = current.race.copy(
                isSaved = nextSaved,
                savedCount = (current.race.savedCount + savedCountDelta).coerceAtLeast(0),
            ),
            isSaving = true,
            saveErrorMessage = null,
        )

        viewModelScope.launch {
            val result = if (nextSaved) {
                safeApiCall<Unit> { raceApi.saveRace(raceId) }
            } else {
                safeApiCall<Unit> { raceApi.unsaveRace(raceId) }
            }

            val latest = _uiState.value as? RaceDetailUiState.Success ?: return@launch
            _uiState.value = when (result) {
                is UiResult.Success -> latest.copy(isSaving = false)
                is UiResult.Error -> latest.copy(
                    race = latest.race.copy(
                        isSaved = wasSaved,
                        savedCount = (latest.race.savedCount - savedCountDelta).coerceAtLeast(0),
                    ),
                    isSaving = false,
                    saveErrorMessage = result.message,
                )
                UiResult.Loading -> latest
            }
        }
    }
}
