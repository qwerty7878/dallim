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
        // S-85 진입 카드 노출 여부 — GET /races/{id}/course 응답의 hasCourse. detail 응답에는
        // 이 필드가 없어 상세 로드 시 함께 조회한다(아래 load() 참고). 조회 실패 시 안전하게
        // false(카드 미노출)로 처리한다 — 이 화면의 핵심 기능이 아니라서 별도 에러 상태를 두지 않는다.
        val hasCourse: Boolean = false,
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

    // RaceDetailRoute가 S-85(코스 미리 달리기) 진입 콜백에 raceId를 그대로 넘겨줘야 해서 non-private.
    val raceId: String =
        checkNotNull(savedStateHandle[DallimDestinations.ARG_RACE_ID]) { "raceId 인자가 없습니다." }

    private val _uiState = MutableStateFlow<RaceDetailUiState>(RaceDetailUiState.Loading)
    val uiState: StateFlow<RaceDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RaceDetailUiState.Loading
            val detailResult = safeApiCall { raceApi.getRaceDetail(raceId) }
            _uiState.value = when (detailResult) {
                is UiResult.Success -> {
                    // hasCourse는 detail 응답에 없어 상세 로드와 함께 조회한다(작업 지시 3번).
                    // 이 카드는 화면의 핵심 경로가 아니므로 실패해도 카드만 숨기고 상세 자체는
                    // 정상 표시한다.
                    val hasCourse = (safeApiCall { raceApi.getRaceCourse(raceId) } as? UiResult.Success)
                        ?.data?.hasCourse == true
                    RaceDetailUiState.Success(race = detailResult.data, hasCourse = hasCourse)
                }
                is UiResult.Error -> RaceDetailUiState.Error(detailResult.message)
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
