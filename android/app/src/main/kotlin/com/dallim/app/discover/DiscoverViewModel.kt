package com.dallim.app.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.onboarding.firstroute.CurrentLocationProvider
import com.dallim.network.route.RouteApi
import com.dallim.network.route.RouteListItem
import com.dallim.network.user.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** GET /routes 의 minDistanceKm/maxDistanceKm 쿼리와 대응하는 거리 필터 프리셋. */
enum class DistanceFilter(val label: String, val minKm: Double?, val maxKm: Double?) {
    ALL("전체 거리", null, null),
    UNDER_3("~3km", null, 3.0),
    RANGE_3_6("3~6km", 3.0, 6.0),
    OVER_6("6km~", 6.0, null),
}

/**
 * GET /routes 의 status 쿼리(docs/02-api-spec.md 4장)와 대응하는 상태 필터.
 * `UNDER_REVIEW`는 Route 상태가 아니라 러닝 완주 판정 상태라 코스 목록 필터에는 없다
 * (docs/01-feature-spec.md §2.2 C — Route 상태 전이는 DISCOVERY→VERIFIED→POPULAR 뿐).
 */
enum class RouteStatusFilter(val label: String, val apiValue: String?) {
    ALL("전체 상태", null),
    DISCOVERY("DISCOVERY", "DISCOVERY"),
    VERIFIED("VERIFIED", "VERIFIED"),
    POPULAR("POPULAR", "POPULAR"),
}

/** GET /routes 의 sort 쿼리와 대응. */
enum class SortOption(val label: String, val apiValue: String) {
    POPULAR("인기순", "popular"),
    NEAR("거리순", "near"),
    NEW("최신순", "new"),
}

data class DiscoverUiState(
    val items: List<RouteListItem> = emptyList(),
    val totalCount: Int = 0,
    val page: Int = 0,
    val hasMore: Boolean = true,
    val isLoadingInitial: Boolean = true,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val distanceFilter: DistanceFilter = DistanceFilter.ALL,
    val statusFilter: RouteStatusFilter = RouteStatusFilter.ALL,
    val sort: SortOption = SortOption.POPULAR,
    /** POST/DELETE saved-routes 요청이 진행 중인 routeId — 중복 탭 방지용. */
    val togglingSaveRouteIds: Set<String> = emptySet(),
)

/**
 * S-11 탐색(코스 리스트) — 필터 적용 코스 목록 + 무한스크롤 (docs/01-feature-spec.md §1.2).
 *
 * 필터는 `GET /routes`가 실제로 지원하는 파라미터(거리/상태/정렬)만 노출한다.
 * feature-spec 원문은 "난이도/신호등/평지" 필터도 언급하지만, RouteApi.getRoutes의 쿼리
 * 파라미터와 [RouteListItem]에는 difficulty/trafficLightCount 필드가 없어(docs/02-api-spec.md
 * 4장) 구현할 수 없다 — SPEC을 임의로 확장하지 않는다는 원칙에 따라 보류한다.
 */
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val routeApi: RouteApi,
    private val userApi: UserApi,
    private val locationProvider: CurrentLocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private var location: Pair<Double, Double>? = null

    init {
        viewModelScope.launch {
            location = locationProvider.getCurrentLocation()
            loadPage(reset = true)
        }
    }

    fun onDistanceFilterChange(filter: DistanceFilter) {
        if (_uiState.value.distanceFilter == filter) return
        _uiState.update { it.copy(distanceFilter = filter) }
        reload()
    }

    fun onStatusFilterChange(filter: RouteStatusFilter) {
        if (_uiState.value.statusFilter == filter) return
        _uiState.update { it.copy(statusFilter = filter) }
        reload()
    }

    fun onSortChange(sort: SortOption) {
        if (_uiState.value.sort == sort) return
        _uiState.update { it.copy(sort = sort) }
        reload()
    }

    /**
     * 저장/저장취소 토글 — POST|DELETE /users/me/saved-routes/{routeId}.
     * 낙관적 업데이트로 아이콘을 즉시 반영하고, 실패 시 원래 상태로 되돌린다
     * (SavedRoutesViewModel.onUnsave와 동일한 removing-set 패턴, 다만 여기서는 아이템을
     * 목록에서 지우는 대신 isSaved 플래그만 뒤집는다).
     */
    fun onToggleSave(routeId: String) {
        val current = _uiState.value
        if (routeId in current.togglingSaveRouteIds) return
        val target = current.items.find { it.routeId == routeId } ?: return
        val nextSaved = !target.isSaved

        _uiState.update { state ->
            state.copy(
                items = state.items.map { if (it.routeId == routeId) it.copy(isSaved = nextSaved) else it },
                togglingSaveRouteIds = state.togglingSaveRouteIds + routeId,
            )
        }

        viewModelScope.launch {
            val result = if (nextSaved) {
                safeApiCall<Unit> { userApi.saveRoute(routeId) }
            } else {
                safeApiCall<Unit> { userApi.unsaveRoute(routeId) }
            }

            _uiState.update { state ->
                val toggling = state.togglingSaveRouteIds - routeId
                when (result) {
                    is UiResult.Success -> state.copy(togglingSaveRouteIds = toggling)
                    is UiResult.Error -> state.copy(
                        items = state.items.map { if (it.routeId == routeId) it.copy(isSaved = !nextSaved) else it },
                        togglingSaveRouteIds = toggling,
                    )
                    UiResult.Loading -> state
                }
            }
        }
    }

    fun retry() = reload()

    /** LazyColumn이 리스트 끝 근처에 도달했을 때 호출 — 다음 페이지를 이어붙인다. */
    fun loadNextPage() {
        val current = _uiState.value
        if (current.isLoadingInitial || current.isLoadingMore || !current.hasMore) return
        viewModelScope.launch { loadPage(reset = false) }
    }

    private fun reload() {
        viewModelScope.launch { loadPage(reset = true) }
    }

    private suspend fun loadPage(reset: Boolean) {
        val stateBefore = _uiState.value
        val targetPage = if (reset) 0 else stateBefore.page + 1
        _uiState.update {
            if (reset) it.copy(isLoadingInitial = true, errorMessage = null) else it.copy(isLoadingMore = true)
        }

        val filters = _uiState.value
        val result = safeApiCall {
            routeApi.getRoutes(
                lat = location?.first,
                lng = location?.second,
                minDistanceKm = filters.distanceFilter.minKm,
                maxDistanceKm = filters.distanceFilter.maxKm,
                status = filters.statusFilter.apiValue,
                sort = filters.sort.apiValue,
                page = targetPage,
                size = PAGE_SIZE,
            )
        }

        _uiState.update { current ->
            when (result) {
                is UiResult.Success -> current.copy(
                    items = if (reset) result.data.items else current.items + result.data.items,
                    totalCount = result.data.totalCount,
                    page = targetPage,
                    hasMore = (targetPage + 1) * PAGE_SIZE < result.data.totalCount,
                    isLoadingInitial = false,
                    isLoadingMore = false,
                    errorMessage = null,
                )
                is UiResult.Error -> current.copy(
                    isLoadingInitial = false,
                    isLoadingMore = false,
                    errorMessage = if (reset) result.message else current.errorMessage,
                )
                UiResult.Loading -> current
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
