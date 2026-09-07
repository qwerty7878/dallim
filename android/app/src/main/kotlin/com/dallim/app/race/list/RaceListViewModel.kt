package com.dallim.app.race.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.network.race.RaceApi
import com.dallim.network.race.RaceSummaryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * `GET /races`의 `region` 쿼리와 대응하는 지역 필터 프리셋 — 로컬 시드 데이터(서울/성남/수원/
 * 안양/과천, docs/02-api-spec.md 16장 브리핑)에 맞춘 목록. "지역 문자열 부분 일치"라 목록에
 * 없는 지역이 나와도 필터 없음(전체)으로는 항상 조회된다.
 */
enum class RaceRegionFilter(val label: String, val apiValue: String?) {
    ALL("전체 지역", null),
    SEOUL("서울", "서울"),
    SEONGNAM("성남", "성남"),
    SUWON("수원", "수원"),
    ANYANG("안양", "안양"),
    GWACHEON("과천", "과천"),
}

/** `GET /races`의 `category` 쿼리(docs/02-api-spec.md 16.2)와 대응. */
enum class RaceCategoryFilter(val label: String, val apiValue: String?) {
    ALL("전체 종목", null),
    FIVE_K("5K", "5K"),
    TEN_K("10K", "10K"),
    HALF("하프", "HALF"),
    FULL("풀", "FULL"),
    ULTRA("울트라", "ULTRA"),
    TRAIL("트레일", "TRAIL"),
}

/** `GET /races`의 `status` 쿼리(계산값 기준 필터)와 대응. */
enum class RaceStatusFilter(val label: String, val apiValue: String?) {
    ALL("전체 상태", null),
    UPCOMING("접수 예정", "UPCOMING"),
    OPEN("접수중", "OPEN"),
    CLOSED("접수 마감", "CLOSED"),
}

data class RaceListUiState(
    val items: List<RaceSummaryItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val regionFilter: RaceRegionFilter = RaceRegionFilter.ALL,
    val categoryFilter: RaceCategoryFilter = RaceCategoryFilter.ALL,
    val statusFilter: RaceStatusFilter = RaceStatusFilter.ALL,
    /** POST/DELETE races/{id}/save 요청이 진행 중인 raceId — 중복 탭 방지용. */
    val togglingSaveRaceIds: Set<String> = emptySet(),
)

/**
 * S-80 대회 캘린더(리스트 뷰만, 캘린더 뷰는 이번 라운드 범위 밖 — docs/02-api-spec.md 16.6).
 * 이번 라운드는 지역/종목/접수상태 필터만 노출한다(참가비 범위 필터는 API에 없어 보류).
 * 시드 데이터가 8건뿐이라 무한스크롤 없이 한 번에 넉넉한 페이지 크기로 불러온다(과설계 금지) —
 * 서버가 이미 접수 마감 임박순으로 정렬해서 내려주므로 클라이언트 재정렬도 하지 않는다.
 */
@HiltViewModel
class RaceListViewModel @Inject constructor(
    private val raceApi: RaceApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RaceListUiState())
    val uiState: StateFlow<RaceListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onRegionFilterChange(filter: RaceRegionFilter) {
        if (_uiState.value.regionFilter == filter) return
        _uiState.update { it.copy(regionFilter = filter) }
        load()
    }

    fun onCategoryFilterChange(filter: RaceCategoryFilter) {
        if (_uiState.value.categoryFilter == filter) return
        _uiState.update { it.copy(categoryFilter = filter) }
        load()
    }

    fun onStatusFilterChange(filter: RaceStatusFilter) {
        if (_uiState.value.statusFilter == filter) return
        _uiState.update { it.copy(statusFilter = filter) }
        load()
    }

    fun retry() = load()

    /**
     * 담기/담기취소 토글 — 낙관적 업데이트 후 실패 시 원복(DiscoverViewModel.onToggleSave와
     * 동일한 패턴). `savedCount`도 함께 +-1 해서 카드의 "달림 러너 N명 참가 예정" 문구가 즉시
     * 반영되게 한다.
     */
    fun onToggleSaveClick(raceId: String) {
        val current = _uiState.value
        if (raceId in current.togglingSaveRaceIds) return
        val target = current.items.find { it.raceId == raceId } ?: return
        val nextSaved = !target.isSaved
        val savedCountDelta = if (nextSaved) 1 else -1

        _uiState.update { state ->
            state.copy(
                items = state.items.map {
                    if (it.raceId == raceId) {
                        it.copy(isSaved = nextSaved, savedCount = (it.savedCount + savedCountDelta).coerceAtLeast(0))
                    } else {
                        it
                    }
                },
                togglingSaveRaceIds = state.togglingSaveRaceIds + raceId,
            )
        }

        viewModelScope.launch {
            val result = if (nextSaved) {
                safeApiCall<Unit> { raceApi.saveRace(raceId) }
            } else {
                safeApiCall<Unit> { raceApi.unsaveRace(raceId) }
            }

            _uiState.update { state ->
                val toggling = state.togglingSaveRaceIds - raceId
                when (result) {
                    is UiResult.Success -> state.copy(togglingSaveRaceIds = toggling)
                    is UiResult.Error -> state.copy(
                        items = state.items.map {
                            if (it.raceId == raceId) {
                                it.copy(isSaved = !nextSaved, savedCount = (it.savedCount - savedCountDelta).coerceAtLeast(0))
                            } else {
                                it
                            }
                        },
                        togglingSaveRaceIds = toggling,
                    )
                    UiResult.Loading -> state
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val filters = _uiState.value
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = safeApiCall {
                raceApi.getRaces(
                    region = filters.regionFilter.apiValue,
                    category = filters.categoryFilter.apiValue,
                    status = filters.statusFilter.apiValue,
                    page = 0,
                    size = PAGE_SIZE,
                )
            }

            _uiState.update { current ->
                when (result) {
                    is UiResult.Success -> current.copy(items = result.data.items, isLoading = false, errorMessage = null)
                    is UiResult.Error -> current.copy(isLoading = false, errorMessage = result.message)
                    UiResult.Loading -> current
                }
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
