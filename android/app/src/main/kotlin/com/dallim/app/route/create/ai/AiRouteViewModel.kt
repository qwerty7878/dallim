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
import com.dallim.network.route.RouteWaypoint
import com.dallim.ui.components.GeoPoint
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
    /** 처음 지도를 띄울 위치 — 현재 GPS 위치, 못 가져오면 null(네이버맵 기본 위치로 뜬다). */
    val initialCenter: GeoPoint? = null,
    /**
     * "꼭 지나갈 장소" — 지도 롱프레스로 최대 [MAX_REQUIRED_WAYPOINTS]곳 지정
     * (docs/02-api-spec.md 11.2). 이미 꽉 찼으면 새 롱프레스는 무시되고, 마커를 탭하면 그 항목만
     * 삭제된다. 비어 있으면 생성 요청에서 필드를 빈 리스트로 보낸다.
     */
    val requiredWaypoints: List<GeoPoint> = emptyList(),
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

    init {
        viewModelScope.launch {
            val location = locationProvider.getCurrentLocation()
            if (location != null) {
                _uiState.update { it.copy(initialCenter = GeoPoint(lng = location.second, lat = location.first)) }
            }
        }
    }

    fun onDistanceChange(km: Float) {
        _uiState.update { it.copy(targetDistanceKm = km) }
    }

    fun onPaceSelected(pace: ComfortablePace?) {
        _uiState.update { it.copy(pace = if (it.pace == pace) null else pace) }
    }

    /** 지도 롱프레스 — 이미 [MAX_REQUIRED_WAYPOINTS]개면 무시(docs/02-api-spec.md 11.2). */
    fun onWaypointLongPress(point: GeoPoint) {
        _uiState.update {
            if (it.requiredWaypoints.size >= MAX_REQUIRED_WAYPOINTS) it else it.copy(requiredWaypoints = it.requiredWaypoints + point)
        }
    }

    /** 마커 탭 — 그 지점 하나만 삭제한다. */
    fun onWaypointClick(point: GeoPoint) {
        _uiState.update { it.copy(requiredWaypoints = it.requiredWaypoints - point) }
    }

    private companion object {
        const val MAX_REQUIRED_WAYPOINTS = 3
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
                requiredWaypoints = state.requiredWaypoints.map { RouteWaypoint(lat = it.lat, lng = it.lng) },
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
