package com.dallim.app.running.prepare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
import com.dallim.app.onboarding.firstroute.CurrentLocationProvider
import com.dallim.network.route.RouteApi
import com.dallim.network.route.RouteDetailResponseBody
import com.dallim.network.run.ClientDeviceInfo
import com.dallim.network.run.RunApi
import com.dallim.network.run.StartRunRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** S-20 "GPS 신호 강도 체크" 결과 등급. */
enum class GpsSignalStrength { CHECKING, STRONG, MODERATE, WEAK, UNAVAILABLE }

sealed interface RunPrepareUiState {
    data object Loading : RunPrepareUiState
    data class Error(val message: String) : RunPrepareUiState

    /** [route]가 null이면 자유 러닝(2026-09-26, 사용자 요청 — 코스 미선택 바로 시작)이다. */
    data class Ready(
        val route: RouteDetailResponseBody?,
        val gpsSignal: GpsSignalStrength = GpsSignalStrength.CHECKING,
        val gpsAccuracyM: Float? = null,
        val isBatteryOptimizationIgnored: Boolean = false,
        val hasBackgroundLocationPermission: Boolean = false,
        /** null = 카운트다운 시작 전. 0이 되는 순간 러닝 시작 API를 호출한다. */
        val countdownSecondsRemaining: Int? = null,
        val isStarting: Boolean = false,
        val startError: String? = null,
    ) : RunPrepareUiState

    /** 카운트다운 + 러닝 시작 API 성공 — 화면(Route)이 S-21로 내비게이션할 차례. */
    data class Started(val runId: String, val routeId: String?) : RunPrepareUiState
}

/**
 * S-20 러닝 준비 (docs/01-feature-spec.md §1.3):
 * GPS 신호 강도 체크 -> 배터리 최적화 안내 -> **백그라운드 위치 권한 최초 요청**(온보딩 S-05가
 * 아니라 여기서) -> 3초 카운트다운 -> [RunApi.startRun].
 *
 * 배터리 최적화 제외/백그라운드 위치 권한은 S-05와 같은 원칙으로 "허용/거부 무관 진행" —
 * 체크리스트로 상태만 보여주고 달리기 시작 자체를 막지 않는다. **단, 포그라운드 위치 권한은
 * 예외다**: `FOREGROUND_SERVICE_TYPE_LOCATION`으로 [com.dallim.app.location.LocationTrackingService]를
 * 띄우려면 플랫폼이 `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` 중 하나가 이미 허용돼 있을
 * 것을 강제한다(없으면 `SecurityException`으로 서비스가 즉시 크래시). 그래서 "달리기 시작" 탭
 * 핸들러(`RunPrepareRoute`)는 포그라운드 위치 권한이 없으면 이 메서드를 호출하는 대신 권한
 * 요청을 먼저 띄우고, 사용자가 다시 탭했을 때만 여기로 들어온다.
 */
@HiltViewModel
class RunPrepareViewModel @Inject constructor(
    private val routeApi: RouteApi,
    private val runApi: RunApi,
    private val locationProvider: CurrentLocationProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** null이면 자유 러닝(2026-09-26, 사용자 요청) — 코스를 먼저 고르지 않고 바로 시작한다. */
    val routeId: String? = savedStateHandle[DallimDestinations.ARG_ROUTE_ID]

    private val _uiState = MutableStateFlow<RunPrepareUiState>(RunPrepareUiState.Loading)
    val uiState: StateFlow<RunPrepareUiState> = _uiState.asStateFlow()

    private var lastGpsAccuracyM: Float? = null

    init {
        load()
    }

    fun load() {
        val currentRouteId = routeId
        if (currentRouteId == null) {
            // 자유 러닝 — 조회할 코스가 없으니 바로 준비 상태로 진입한다.
            _uiState.value = RunPrepareUiState.Ready(route = null)
            checkGpsSignal()
            return
        }
        viewModelScope.launch {
            _uiState.value = RunPrepareUiState.Loading
            when (val result = safeApiCall { routeApi.getRouteDetail(currentRouteId) }) {
                is UiResult.Success -> {
                    _uiState.value = RunPrepareUiState.Ready(route = result.data)
                    checkGpsSignal()
                }
                is UiResult.Error -> _uiState.value = RunPrepareUiState.Error(result.message)
                UiResult.Loading -> Unit
            }
        }
    }

    fun checkGpsSignal() {
        viewModelScope.launch {
            updateReady { it.copy(gpsSignal = GpsSignalStrength.CHECKING) }
            val sample = locationProvider.getCurrentLocationSample()
            lastGpsAccuracyM = sample?.accuracyM
            val strength = when {
                sample == null -> GpsSignalStrength.UNAVAILABLE
                sample.accuracyM <= 15f -> GpsSignalStrength.STRONG
                sample.accuracyM <= 40f -> GpsSignalStrength.MODERATE
                else -> GpsSignalStrength.WEAK
            }
            updateReady { it.copy(gpsSignal = strength, gpsAccuracyM = sample?.accuracyM) }
        }
    }

    /** [android.os.PowerManager.isIgnoringBatteryOptimizations] 결과를 화면에서 확인해 전달한다. */
    fun onBatteryOptimizationStatusChanged(isIgnoringOptimizations: Boolean) {
        updateReady { it.copy(isBatteryOptimizationIgnored = isIgnoringOptimizations) }
    }

    /** 백그라운드 위치 권한 다이얼로그 결과(혹은 API<29라 애초에 불필요함)를 화면에서 전달한다. */
    fun onBackgroundLocationPermissionChanged(granted: Boolean) {
        updateReady { it.copy(hasBackgroundLocationPermission = granted) }
    }

    /** 3초 카운트다운을 시작하고, 끝나면 [startRun]을 호출한다. */
    fun onStartRunClick() {
        val current = _uiState.value as? RunPrepareUiState.Ready ?: return
        if (current.countdownSecondsRemaining != null) return

        viewModelScope.launch {
            for (remaining in COUNTDOWN_SECONDS downTo 1) {
                updateReady { it.copy(countdownSecondsRemaining = remaining) }
                delay(1000)
            }
            startRun()
        }
    }

    fun onCancelCountdown() {
        updateReady { it.copy(countdownSecondsRemaining = null) }
    }

    private fun startRun() {
        val current = _uiState.value as? RunPrepareUiState.Ready ?: return
        viewModelScope.launch {
            updateReady { it.copy(isStarting = true, startError = null) }
            val request = StartRunRequest(
                routeId = routeId,
                mode = if (routeId == null) "FREE" else "SOLO",
                startedAt = Instant.now().toString(),
                clientDeviceInfo = ClientDeviceInfo(gpsAccuracyM = (lastGpsAccuracyM ?: 0f).toInt()),
            )
            when (val result = safeApiCall { runApi.startRun(request) }) {
                is UiResult.Success -> _uiState.value = RunPrepareUiState.Started(
                    runId = result.data.runId,
                    routeId = routeId,
                )
                is UiResult.Error -> updateReady {
                    it.copy(isStarting = false, countdownSecondsRemaining = null, startError = result.message)
                }
                UiResult.Loading -> Unit
            }
        }
    }

    private inline fun updateReady(transform: (RunPrepareUiState.Ready) -> RunPrepareUiState.Ready) {
        _uiState.update { state -> if (state is RunPrepareUiState.Ready) transform(state) else state }
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 3
    }
}
