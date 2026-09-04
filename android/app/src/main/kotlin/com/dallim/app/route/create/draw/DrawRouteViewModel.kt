package com.dallim.app.route.create.draw

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.onboarding.firstroute.CurrentLocationProvider
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.route.RouteApi
import com.dallim.network.route.RouteDrawConvertRequest
import com.dallim.network.route.RouteDrawConvertResponseBody
import com.dallim.ui.components.GeoPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DrawRouteUiState(
    /** 지도 위 드래그로 쌓은 원본 점들 — GeoJSON 순서(lng, lat)와 맞춘 [GeoPoint]로 보관한다. */
    val drawnPoints: List<GeoPoint> = emptyList(),
    /** 처음 지도를 띄울 위치 — 현재 GPS 위치, 못 가져오면 null(네이버맵 기본 위치로 뜬다). */
    val initialCenter: GeoPoint? = null,
    val isConverting: Boolean = false,
    val convertResult: RouteDrawConvertResponseBody? = null,
    val errorMessage: String? = null,
    /**
     * "출발점으로 돌아오기" 토글 — 기본 false(직선 코스로 뛰고 싶은 사람도 있음, docs/01-feature-spec.md
     * 1.6). true일 때만 draw-convert 요청에 closeLoop: true를 실어 보낸다(docs/02-api-spec.md 11.1).
     */
    val closeLoop: Boolean = false,
    /** "저장" 탭 시 보여줄 스낵바 메시지 — 실제 저장 API는 이번 라운드 범위 밖(docs/02-api-spec.md 8.3). */
    val snackbarMessage: String? = null,
) {
    val canConvert: Boolean get() = drawnPoints.size >= MIN_DRAW_POINTS && !isConverting

    private companion object {
        /** 서버 DRAW_TOO_SHORT 기준(10개 미만)과 동일 — 불필요한 요청을 미리 막는다. */
        const val MIN_DRAW_POINTS = 10
    }
}

/**
 * S-44 직접 그리기 — 손가락 드래그로 그린 궤적을 `POST /routes/draw-convert`로 실도로에 스냅한다
 * (docs/02-api-spec.md 8.1). 완주 판정과 무관한 화면이라 서버 응답을 그대로 신뢰하고 덮어쓴다.
 */
@HiltViewModel
class DrawRouteViewModel @Inject constructor(
    private val routeApi: RouteApi,
    private val locationProvider: CurrentLocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrawRouteUiState())
    val uiState: StateFlow<DrawRouteUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val location = locationProvider.getCurrentLocation()
            if (location != null) {
                _uiState.update { it.copy(initialCenter = GeoPoint(lng = location.second, lat = location.first)) }
            }
        }
    }

    /** [com.dallim.ui.components.NaverDrawableMapView]의 드래그 콜백 — 매 샘플 점마다 호출된다. */
    fun onPointDrawn(point: GeoPoint) {
        _uiState.update { it.copy(drawnPoints = it.drawnPoints + point) }
    }

    /** "지우기" — 처음부터 다시 그릴 수 있게 초기화한다. */
    fun onClearClick() {
        _uiState.update { it.copy(drawnPoints = emptyList(), convertResult = null, errorMessage = null) }
    }

    /** "출발점으로 돌아오기" 토글 — 기본 꺼짐, 그리는 동안 언제든 켜고 끌 수 있다. */
    fun onCloseLoopToggle(checked: Boolean) {
        _uiState.update { it.copy(closeLoop = checked) }
    }

    /** "완료" — 그린 궤적을 서버로 보내 실도로 경로로 변환한다. */
    fun onConvertClick() {
        val state = _uiState.value
        if (!state.canConvert) return

        viewModelScope.launch {
            _uiState.update { it.copy(isConverting = true, errorMessage = null) }

            val request = RouteDrawConvertRequest(
                drawnPath = GeoJsonLineString(coordinates = state.drawnPoints.map { listOf(it.lng, it.lat) }),
                closeLoop = state.closeLoop,
            )
            val result = safeApiCall { routeApi.convertDrawnPath(request) }

            _uiState.update { current ->
                when (result) {
                    is UiResult.Success -> current.copy(isConverting = false, convertResult = result.data, errorMessage = null)
                    is UiResult.Error -> current.copy(isConverting = false, errorMessage = result.message)
                    UiResult.Loading -> current
                }
            }
        }
    }

    /** "다시 그리기" — 변환 결과를 버리고 빈 캔버스로 돌아간다. */
    fun onRedrawClick() {
        _uiState.update { it.copy(drawnPoints = emptyList(), convertResult = null, errorMessage = null) }
    }

    /** "저장" — 코스를 실제로 저장하는 API는 이번 라운드 범위 밖(docs/02-api-spec.md 8.3). */
    fun onSaveClick() {
        _uiState.update { it.copy(snackbarMessage = "저장 기능은 준비 중이에요") }
    }

    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
