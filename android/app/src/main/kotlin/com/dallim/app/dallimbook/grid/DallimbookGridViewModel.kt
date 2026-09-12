package com.dallim.app.dallimbook.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.network.dallimbook.DallimbookApi
import com.dallim.network.dallimbook.DallimbookRunItem
import com.dallim.network.user.SavedRouteItem
import com.dallim.network.user.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 그리드 한 칸 — 완주한 작품이거나, 저장했지만 아직 그리지 않은 코스의 빈 슬롯이다. */
sealed interface DallimbookSlot {
    data class Artwork(val run: DallimbookRunItem) : DallimbookSlot
    data class Empty(val savedRoute: SavedRouteItem) : DallimbookSlot
}

data class DallimbookGridUiState(
    val emptySlots: List<DallimbookSlot.Empty> = emptyList(),
    val artworkSlots: List<DallimbookSlot.Artwork> = emptyList(),
    val totalCount: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val bestPaceSecPerKm: Int? = null,
    val averagePaceSecPerKm: Int? = null,
    val page: Int = 0,
    val hasMore: Boolean = true,
    val isLoadingInitial: Boolean = true,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
) {
    /** 빈 슬롯을 앞에 두어 "저장했지만 아직 안 그린 코스"를 먼저 눈에 띄게 한다. */
    val slots: List<DallimbookSlot> get() = emptySlots + artworkSlots
}

/**
 * S-40 달림북 그리드 (docs/01-feature-spec.md §1.4) — 완주 GPS 그림 2열 그리드 + 빈 슬롯.
 *
 * 두 개의 독립된 API를 조합한다:
 * - `GET /users/me/runs`([DallimbookApi]) — 완주 기록, 무한스크롤 페이지네이션 대상.
 * - `GET /users/me/saved-routes`([UserApi]) — `hasRun=false`인 항목만 "저장했지만 미완주"
 *   빈 슬롯으로 사용한다. 저장 코스는 완주 기록만큼 쌓이지 않는 개인 목록이라 한 번에 모두
 *   불러오고(최대 [SAVED_ROUTES_FETCH_SIZE]개), 무한스크롤은 완주 기록 쪽에만 적용한다.
 */
@HiltViewModel
class DallimbookGridViewModel @Inject constructor(
    private val dallimbookApi: DallimbookApi,
    private val userApi: UserApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DallimbookGridUiState())
    val uiState: StateFlow<DallimbookGridUiState> = _uiState.asStateFlow()

    init {
        retry()
    }

    fun retry() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInitial = true, errorMessage = null) }
            loadEmptySlots()
            loadRunPage(reset = true)
        }
    }

    /** LazyVerticalGrid가 끝에 가까워졌을 때 호출 — 완주 기록 다음 페이지만 이어붙인다. */
    fun loadNextPage() {
        val current = _uiState.value
        if (current.isLoadingInitial || current.isLoadingMore || !current.hasMore) return
        viewModelScope.launch { loadRunPage(reset = false) }
    }

    private suspend fun loadEmptySlots() {
        val result = safeApiCall { userApi.getSavedRoutes(page = 0, size = SAVED_ROUTES_FETCH_SIZE) }
        if (result is UiResult.Success) {
            val emptySlots = result.data.items
                .filterNot { it.hasRun }
                .map { DallimbookSlot.Empty(it) }
            _uiState.update { it.copy(emptySlots = emptySlots) }
        }
        // 저장 코스 조회 실패는 조용히 무시한다 — 완주 기록(핵심 콘텐츠)이 정상이면 그리드는
        // 여전히 유효하고, 에러 배너는 완주 기록 로딩 실패에만 쓴다.
    }

    private suspend fun loadRunPage(reset: Boolean) {
        val stateBefore = _uiState.value
        val targetPage = if (reset) 0 else stateBefore.page + 1
        _uiState.update {
            if (reset) it.copy(isLoadingInitial = true, errorMessage = null) else it.copy(isLoadingMore = true)
        }

        val result = safeApiCall { dallimbookApi.getMyRuns(page = targetPage, size = PAGE_SIZE) }

        _uiState.update { current ->
            when (result) {
                is UiResult.Success -> current.copy(
                    artworkSlots = if (reset) {
                        result.data.items.map { DallimbookSlot.Artwork(it) }
                    } else {
                        current.artworkSlots + result.data.items.map { DallimbookSlot.Artwork(it) }
                    },
                    totalCount = result.data.totalCount,
                    totalDistanceKm = result.data.totalDistanceKm,
                    bestPaceSecPerKm = result.data.bestPaceSecPerKm,
                    averagePaceSecPerKm = result.data.averagePaceSecPerKm,
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
        const val SAVED_ROUTES_FETCH_SIZE = 50
    }
}
