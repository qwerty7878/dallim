package com.dallim.app.route.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.network.user.SavedRouteItem
import com.dallim.network.user.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavedRoutesUiState(
    val items: List<SavedRouteItem> = emptyList(),
    val totalCount: Int = 0,
    val page: Int = 0,
    val hasMore: Boolean = true,
    val isLoadingInitial: Boolean = true,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val removingRouteIds: Set<String> = emptySet(),
)

/**
 * S-17 저장한 코스 — 내가 저장한 코스 목록 + 저장 취소 (docs/01-feature-spec.md §1.2,
 * docs/02-api-spec.md 2장 GET/POST/DELETE /users/me/saved-routes).
 */
@HiltViewModel
class SavedRoutesViewModel @Inject constructor(
    private val userApi: UserApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedRoutesUiState())
    val uiState: StateFlow<SavedRoutesUiState> = _uiState.asStateFlow()

    init {
        loadPage(reset = true)
    }

    fun retry() = loadPage(reset = true)

    fun loadNextPage() {
        val current = _uiState.value
        if (current.isLoadingInitial || current.isLoadingMore || !current.hasMore) return
        loadPage(reset = false)
    }

    /** 저장 취소 — DELETE /users/me/saved-routes/{routeId}. 성공 시 목록에서 즉시 제거. */
    fun onUnsave(routeId: String) {
        if (routeId in _uiState.value.removingRouteIds) return
        viewModelScope.launch {
            _uiState.update { it.copy(removingRouteIds = it.removingRouteIds + routeId) }
            val result = safeApiCall<Unit> { userApi.unsaveRoute(routeId) }
            _uiState.update { current ->
                when (result) {
                    is UiResult.Success -> current.copy(
                        items = current.items.filterNot { it.routeId == routeId },
                        totalCount = (current.totalCount - 1).coerceAtLeast(0),
                        removingRouteIds = current.removingRouteIds - routeId,
                    )
                    is UiResult.Error -> current.copy(
                        removingRouteIds = current.removingRouteIds - routeId,
                        errorMessage = result.message,
                    )
                    UiResult.Loading -> current
                }
            }
        }
    }

    private fun loadPage(reset: Boolean) {
        viewModelScope.launch {
            val stateBefore = _uiState.value
            val targetPage = if (reset) 0 else stateBefore.page + 1
            _uiState.update {
                if (reset) it.copy(isLoadingInitial = true, errorMessage = null) else it.copy(isLoadingMore = true)
            }

            val result = safeApiCall { userApi.getSavedRoutes(page = targetPage, size = PAGE_SIZE) }

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
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
