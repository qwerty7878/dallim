package com.dallim.app.running.navigation

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.location.LocationTrackingService
import com.dallim.app.location.RunFinishCoordinator
import com.dallim.app.location.RunPhase
import com.dallim.app.location.RunTrackingRepository
import com.dallim.app.location.RunTrackingSnapshot
import com.dallim.app.navigation.DallimDestinations
import com.dallim.network.route.RouteApi
import com.dallim.ui.components.GeoPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RunNavigationUiState {
    data object Loading : RunNavigationUiState
    data class Error(val message: String) : RunNavigationUiState
    data class Tracking(val plannedRoute: List<GeoPoint>, val snapshot: RunTrackingSnapshot) : RunNavigationUiState
}

/**
 * S-21 Sketch Navigation + S-22(일시정지/재개/종료) + S-23(코스 이탈) + S-24(완주판정 처리) —
 * destinations 문서 주석대로 이 네 화면 ID는 전부 이 하나의 ViewModel/화면이 담당한다.
 *
 * [LocationTrackingService]를 시작/일시정지/재개/종료 인텐트로 제어하고,
 * [RunTrackingRepository]가 서비스 쪽에서 갱신하는 실시간 상태를 그대로 관찰만 한다 — 화면
 * 자신은 위치 계산을 하지 않는다(모든 GPS 로직은 Service/순수 유틸에 있음).
 */
@HiltViewModel
class RunNavigationViewModel @Inject constructor(
    private val routeApi: RouteApi,
    private val repository: RunTrackingRepository,
    private val finishCoordinator: RunFinishCoordinator,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val runId: String = checkNotNull(savedStateHandle[DallimDestinations.ARG_RUN_ID]) { "runId 인자가 없습니다." }
    private val routeId: String =
        checkNotNull(savedStateHandle[DallimDestinations.ARG_ROUTE_ID]) { "routeId 인자가 없습니다." }

    private val _plannedRouteState = MutableStateFlow<UiResult<List<GeoPoint>>>(UiResult.Loading)

    val uiState: StateFlow<RunNavigationUiState> = combine(
        _plannedRouteState,
        repository.state,
    ) { plannedResult, snapshot ->
        when (plannedResult) {
            UiResult.Loading -> RunNavigationUiState.Loading
            is UiResult.Error -> RunNavigationUiState.Error(plannedResult.message)
            is UiResult.Success -> RunNavigationUiState.Tracking(plannedResult.data, snapshot)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RunNavigationUiState.Loading)

    init {
        loadRouteAndStartTracking()
    }

    private fun loadRouteAndStartTracking() {
        viewModelScope.launch {
            when (val result = safeApiCall { routeApi.getRouteDetail(routeId) }) {
                is UiResult.Success -> {
                    val planned = result.data.geoJson.toLngLatPairs().map { (lng, lat) -> GeoPoint(lng, lat) }
                    _plannedRouteState.value = UiResult.Success(planned)
                    if (repository.state.value.phase == RunPhase.IDLE) {
                        ContextCompat.startForegroundService(context, LocationTrackingService.startIntent(context, runId, planned))
                    }
                }
                is UiResult.Error -> _plannedRouteState.value = UiResult.Error(result.message)
                UiResult.Loading -> Unit
            }
        }
    }

    fun onPauseClick() {
        context.startService(LocationTrackingService.pauseIntent(context))
    }

    fun onResumeClick() {
        context.startService(LocationTrackingService.resumeIntent(context))
    }

    fun onFinishClick() {
        context.startService(LocationTrackingService.finishIntent(context))
        val planned = (_plannedRouteState.value as? UiResult.Success)?.data ?: emptyList()
        viewModelScope.launch { finishCoordinator.finish(runId, planned) }
    }

    fun onRetryFinishClick() {
        val planned = (_plannedRouteState.value as? UiResult.Success)?.data ?: emptyList()
        viewModelScope.launch { finishCoordinator.finish(runId, planned) }
    }
}
