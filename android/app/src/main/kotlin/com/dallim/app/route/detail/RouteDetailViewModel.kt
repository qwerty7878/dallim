package com.dallim.app.route.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
import com.dallim.network.route.FinisherThumbnail
import com.dallim.network.route.RouteApi
import com.dallim.network.route.RouteDetailResponseBody
import com.dallim.network.user.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RouteDetailUiState {
    data object Loading : RouteDetailUiState

    data class Success(
        val route: RouteDetailResponseBody,
        val finishers: List<FinisherThumbnail> = emptyList(),
        val isSaving: Boolean = false,
        val saveErrorMessage: String? = null,
    ) : RouteDetailUiState

    data class Error(val message: String) : RouteDetailUiState
}

/**
 * S-16 Route 상세 — 코스 스펙/러너 GPS 썸네일 (docs/01-feature-spec.md §1.2).
 *
 * 지도 SDK(네이버맵/카카오맵) 실연동은 이번 라운드 범위 밖이다 — API 키/Gradle 의존성이 아직
 * 없으므로 "코스 지도" 영역은 [com.dallim.ui.components.RouteThumbnailView] 기반 정적 렌더링으로
 * 대체한다(RouteDetailScreen 참고). SPEC 축소가 아니라 환경(SDK 키 부재) 제약에 따른 대체다.
 */
@HiltViewModel
class RouteDetailViewModel @Inject constructor(
    private val routeApi: RouteApi,
    private val userApi: UserApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val routeId: String =
        checkNotNull(savedStateHandle[DallimDestinations.ARG_ROUTE_ID]) { "routeId 인자가 없습니다." }

    private val _uiState = MutableStateFlow<RouteDetailUiState>(RouteDetailUiState.Loading)
    val uiState: StateFlow<RouteDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RouteDetailUiState.Loading
            when (val detailResult = safeApiCall { routeApi.getRouteDetail(routeId) }) {
                is UiResult.Success -> {
                    val finishersResult = safeApiCall { routeApi.getFinishers(routeId) }
                    val finishers = (finishersResult as? UiResult.Success)?.data?.items ?: emptyList()
                    _uiState.value = RouteDetailUiState.Success(route = detailResult.data, finishers = finishers)
                }
                is UiResult.Error -> _uiState.value = RouteDetailUiState.Error(detailResult.message)
                UiResult.Loading -> Unit
            }
        }
    }

    /** 저장/저장취소 토글 — POST 또는 DELETE /users/me/saved-routes/{routeId}. */
    fun onToggleSave() {
        val current = _uiState.value as? RouteDetailUiState.Success ?: return
        if (current.isSaving) return

        viewModelScope.launch {
            _uiState.value = current.copy(isSaving = true, saveErrorMessage = null)
            val wasSaved = current.route.isSaved
            val result = safeApiCall<Unit> {
                if (wasSaved) userApi.unsaveRoute(routeId) else userApi.saveRoute(routeId)
            }
            _uiState.value = when (result) {
                is UiResult.Success -> current.copy(
                    route = current.route.copy(isSaved = !wasSaved),
                    isSaving = false,
                )
                is UiResult.Error -> current.copy(isSaving = false, saveErrorMessage = result.message)
                UiResult.Loading -> current
            }
        }
    }
}
