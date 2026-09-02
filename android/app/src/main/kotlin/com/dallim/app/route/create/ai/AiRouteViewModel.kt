package com.dallim.app.route.create.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.onboarding.firstroute.CurrentLocationProvider
import com.dallim.app.onboarding.profile.ComfortablePace
import com.dallim.network.route.RouteApi
import com.dallim.network.route.RouteDiscoveryRequest
import com.dallim.network.route.RouteDiscoveryResponseBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiRouteUiState(
    val targetDistanceKm: Float = 5f,
    val pace: ComfortablePace? = null,
    val isGenerating: Boolean = false,
    val result: RouteDiscoveryResponseBody? = null,
    val errorMessage: String? = null,
    /** "저장" 탭 시 보여줄 스낵바 메시지 — 실제 저장 API는 이번 라운드 범위 밖(docs/02-api-spec.md 8.3). */
    val snackbarMessage: String? = null,
)

/**
 * S-45 AI 자동 생성 — 현재 위치 + 목표 거리(+선택 페이스)로 `POST /routes/discovery`를 호출해
 * 순환 코스를 만든다 (docs/02-api-spec.md 8.2). 매 호출마다 서버가 반지름에 무작위 편차를 주므로
 * "다시 생성"을 누르면 같은 입력이라도 다른 코스가 나온다 — 이게 "수정" 체감을 준다.
 */
@HiltViewModel
class AiRouteViewModel @Inject constructor(
    private val routeApi: RouteApi,
    private val locationProvider: CurrentLocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiRouteUiState())
    val uiState: StateFlow<AiRouteUiState> = _uiState.asStateFlow()

    fun onDistanceChange(km: Float) {
        _uiState.update { it.copy(targetDistanceKm = km) }
    }

    fun onPaceSelected(pace: ComfortablePace?) {
        _uiState.update { it.copy(pace = if (it.pace == pace) null else pace) }
    }

    /** "코스 생성" / "다시 생성" 공용 — 같은 입력이라도 서버가 매번 다른 반지름 편차를 준다. */
    fun onGenerateClick() {
        if (_uiState.value.isGenerating) return

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, errorMessage = null) }

            val location = locationProvider.getCurrentLocation()
            if (location == null) {
                _uiState.update {
                    it.copy(isGenerating = false, errorMessage = "현재 위치를 확인할 수 없어요. 위치 권한을 확인해주세요.")
                }
                return@launch
            }

            val state = _uiState.value
            val request = RouteDiscoveryRequest(
                startLng = location.second,
                startLat = location.first,
                targetDistanceKm = state.targetDistanceKm.toDouble(),
                pace = state.pace?.apiValue,
            )
            val result = safeApiCall { routeApi.discoverRoute(request) }

            _uiState.update { current ->
                when (result) {
                    is UiResult.Success -> current.copy(isGenerating = false, result = result.data, errorMessage = null)
                    is UiResult.Error -> current.copy(isGenerating = false, errorMessage = result.message)
                    UiResult.Loading -> current
                }
            }
        }
    }

    /** "저장" — 코스를 실제로 저장하는 API는 이번 라운드 범위 밖(docs/02-api-spec.md 8.3). */
    fun onSaveClick() {
        _uiState.update { it.copy(snackbarMessage = "저장 기능은 준비 중이에요") }
    }

    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
