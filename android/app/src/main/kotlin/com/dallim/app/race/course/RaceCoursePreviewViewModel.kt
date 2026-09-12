package com.dallim.app.race.course

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
import com.dallim.network.race.RaceApi
import com.dallim.network.race.RaceCourseResponseBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RaceCoursePreviewUiState {
    data object Loading : RaceCoursePreviewUiState

    /**
     * `course.hasCourse == false`도 여기 포함된다 — 화면이 그 값을 보고 빈 상태를 그린다
     * (RaceCoursePreviewScreen 참고). 서버 응답이 정상적으로 왔다는 점에서 Error와 다르다.
     */
    data class Success(val course: RaceCourseResponseBody) : RaceCoursePreviewUiState

    data class Error(val message: String) : RaceCoursePreviewUiState
}

/**
 * S-85 대회 코스 미리 달리기 (docs/02-api-spec.md 16.6 신규). "이 구간 달리기"는 새 엔드포인트가
 * 아니라 [RaceCourseResponseBody.segments]의 `routeId`로 기존 S-20(러닝 준비) 진입을 그대로
 * 재사용하는 것 — 이 ViewModel은 조회만 담당하고 내비게이션은 Route 컴포저블이 처리한다.
 */
@HiltViewModel
class RaceCoursePreviewViewModel @Inject constructor(
    private val raceApi: RaceApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val raceId: String =
        checkNotNull(savedStateHandle[DallimDestinations.ARG_RACE_ID]) { "raceId 인자가 없습니다." }

    private val _uiState = MutableStateFlow<RaceCoursePreviewUiState>(RaceCoursePreviewUiState.Loading)
    val uiState: StateFlow<RaceCoursePreviewUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RaceCoursePreviewUiState.Loading
            _uiState.value = when (val result = safeApiCall { raceApi.getRaceCourse(raceId) }) {
                is UiResult.Success -> RaceCoursePreviewUiState.Success(result.data)
                is UiResult.Error -> RaceCoursePreviewUiState.Error(result.message)
                UiResult.Loading -> RaceCoursePreviewUiState.Loading
            }
        }
    }
}
