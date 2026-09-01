package com.dallim.app.onboarding.firstroute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.network.route.RouteApi
import com.dallim.network.route.RouteListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FirstRouteSuggestionUiState {
    data object Loading : FirstRouteSuggestionUiState
    data class Success(val route: RouteListItem) : FirstRouteSuggestionUiState
    data object Empty : FirstRouteSuggestionUiState
    data object Error : FirstRouteSuggestionUiState
}

/**
 * S-06 첫 코스 제안 — 현재 위치 기준 1.5~2.5km 코스 1개 조회 (docs/01-feature-spec.md 1.1).
 * `GET /routes`는 route 도메인 소속이라 이번 라운드 백엔드 작업 범위(auth)엔 없을 수 있으므로,
 * 네트워크 실패는 [FirstRouteSuggestionUiState.Error]로 흡수하고 화면은 로딩/빈 상태까지만
 * 갖춘다 — 실제 서버 연동 확인은 이번 라운드 범위 밖.
 */
@HiltViewModel
class FirstRouteSuggestionViewModel @Inject constructor(
    private val routeApi: RouteApi,
    private val locationProvider: CurrentLocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FirstRouteSuggestionUiState>(FirstRouteSuggestionUiState.Loading)
    val uiState: StateFlow<FirstRouteSuggestionUiState> = _uiState.asStateFlow()

    init {
        loadSuggestion()
    }

    fun loadSuggestion() {
        viewModelScope.launch {
            _uiState.value = FirstRouteSuggestionUiState.Loading
            val location = locationProvider.getCurrentLocation()
            runCatching {
                routeApi.getRoutes(
                    lat = location?.first,
                    lng = location?.second,
                    minDistanceKm = 1.5,
                    maxDistanceKm = 2.5,
                    sort = "near",
                    page = 0,
                    size = 1,
                )
            }.onSuccess { response ->
                val body = response.body()
                val route = body?.data?.items?.firstOrNull()
                _uiState.value = when {
                    !response.isSuccessful || body?.success != true -> FirstRouteSuggestionUiState.Error
                    route != null -> FirstRouteSuggestionUiState.Success(route)
                    else -> FirstRouteSuggestionUiState.Empty
                }
            }.onFailure {
                _uiState.value = FirstRouteSuggestionUiState.Error
            }
        }
    }
}
