package com.dallim.app.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.network.notification.NotificationApi
import com.dallim.network.notification.NotificationItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationListUiState(
    val items: List<NotificationItem> = emptyList(),
    val totalCount: Int = 0,
    val page: Int = 0,
    val hasMore: Boolean = true,
    val isLoadingInitial: Boolean = true,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * S-46 알림 목록 — 내 알림 최신순 조회 + 항목 탭 시 읽음 처리 (docs/01-feature-spec.md §1.7,
 * docs/02-api-spec.md 9장). 페이지네이션은 [com.dallim.app.route.saved.SavedRoutesViewModel]과
 * 동일한 관례(page/size, `hasMore` 계산)를 따른다.
 */
@HiltViewModel
class NotificationListViewModel @Inject constructor(
    private val notificationApi: NotificationApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationListUiState())
    val uiState: StateFlow<NotificationListUiState> = _uiState.asStateFlow()

    init {
        loadPage(reset = true)
    }

    fun retry() = loadPage(reset = true)

    fun loadNextPage() {
        val current = _uiState.value
        if (current.isLoadingInitial || current.isLoadingMore || !current.hasMore) return
        loadPage(reset = false)
    }

    /**
     * `POST /notifications/{id}/read`는 멱등이므로(docs/02-api-spec.md 9.3) 이미 읽은 알림을 다시
     * 눌러도 그대로 재호출한다. 목록은 낙관적으로 먼저 읽음 처리해 배지/글자 굵기가 즉시 반영되게
     * 하고, 서버 호출 실패는 조용히 무시한다 — 다음에 목록을 새로고침하면 서버 상태와 다시
     * 맞춰지고, 읽음 처리는 재시도해도 부담 없는 멱등 동작이라 사용자에게 에러를 보여줄 정도는
     * 아니다.
     */
    fun onNotificationClick(notificationId: String) {
        _uiState.update { state ->
            state.copy(
                items = state.items.map { if (it.id == notificationId) it.copy(isRead = true) else it },
            )
        }
        viewModelScope.launch {
            safeApiCall<Unit> { notificationApi.markAsRead(notificationId) }
        }
    }

    private fun loadPage(reset: Boolean) {
        viewModelScope.launch {
            val stateBefore = _uiState.value
            val targetPage = if (reset) 0 else stateBefore.page + 1
            _uiState.update {
                if (reset) it.copy(isLoadingInitial = true, errorMessage = null) else it.copy(isLoadingMore = true)
            }

            val result = safeApiCall { notificationApi.getNotifications(page = targetPage, size = PAGE_SIZE) }

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
