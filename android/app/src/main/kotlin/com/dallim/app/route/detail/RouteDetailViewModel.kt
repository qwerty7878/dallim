package com.dallim.app.route.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
import com.dallim.network.meetup.MeetupApi
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
        /**
         * 이 코스에 열려 있는(OPEN 상태 & 예정 시각이 아직 안 지난) 모집 개수 —
         * docs/01-feature-spec.md §1.8.1 "그 코스에 열려 있는 모집(OPEN, 미래 시각) 개수를
         * 보여주고 탭하면 S-47로 이동". `GET /routes/{routeId}/meetups`(14.2)는 개수 필드를
         * 따로 내려주지 않으므로 목록을 받아 클라이언트에서 직접 센다.
         */
        val openMeetupCount: Int = 0,
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
    private val meetupApi: MeetupApi,
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

                    // best-effort: 모집 목록 호출이 실패해도(예: 오프라인) Route 상세 자체는
                    // 정상 표시한다 — finishers와 동일한 방식.
                    val meetupsResult = safeApiCall { meetupApi.getMeetups(routeId) }
                    val openMeetupCount = (meetupsResult as? UiResult.Success)
                        ?.data
                        ?.items
                        ?.count { it.status == "OPEN" && !it.isPast }
                        ?: 0

                    _uiState.value = RouteDetailUiState.Success(
                        route = detailResult.data,
                        finishers = finishers,
                        openMeetupCount = openMeetupCount,
                    )
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
