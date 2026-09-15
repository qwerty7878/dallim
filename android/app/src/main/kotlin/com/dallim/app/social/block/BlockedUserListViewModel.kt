package com.dallim.app.social.block

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.network.user.BlockedUserApi
import com.dallim.network.user.BlockedUserItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BlockedUserListUiState {
    data object Loading : BlockedUserListUiState
    data class Success(
        val items: List<BlockedUserItem> = emptyList(),
        /** DELETE 요청이 진행 중인 userId — 중복 탭 방지용. */
        val unblockingUserIds: Set<String> = emptySet(),
        val errorMessage: String? = null,
    ) : BlockedUserListUiState
    data class Error(val message: String) : BlockedUserListUiState
}

/**
 * 차단 관리 (2026-09-16 신규, 마이 S-42에서 진입 — docs/02-api-spec.md 18.2). 최근 차단순
 * 목록 + 차단 해제. 페이지네이션 없음(서버가 이미 그렇게 응답).
 */
@HiltViewModel
class BlockedUserListViewModel @Inject constructor(
    private val blockedUserApi: BlockedUserApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BlockedUserListUiState>(BlockedUserListUiState.Loading)
    val uiState: StateFlow<BlockedUserListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = BlockedUserListUiState.Loading
            when (val result = safeApiCall { blockedUserApi.getBlockedUsers() }) {
                is UiResult.Success -> _uiState.value = BlockedUserListUiState.Success(items = result.data.items)
                is UiResult.Error -> _uiState.value = BlockedUserListUiState.Error(result.message)
                UiResult.Loading -> Unit
            }
        }
    }

    /** 차단 해제 — 멱등이라 실패해도 굳이 원복하지 않고 에러 메시지만 보여준다(재시도하면 그만). */
    fun onUnblockClick(userId: String) {
        val current = _uiState.value as? BlockedUserListUiState.Success ?: return
        if (userId in current.unblockingUserIds) return

        _uiState.update { state ->
            (state as? BlockedUserListUiState.Success)
                ?.copy(unblockingUserIds = state.unblockingUserIds + userId, errorMessage = null)
                ?: state
        }

        viewModelScope.launch {
            val result = safeApiCall<Unit> { blockedUserApi.unblockUser(userId) }
            _uiState.update { state ->
                val success = state as? BlockedUserListUiState.Success ?: return@update state
                when (result) {
                    is UiResult.Success -> success.copy(
                        items = success.items.filterNot { it.userId == userId },
                        unblockingUserIds = success.unblockingUserIds - userId,
                    )
                    is UiResult.Error -> success.copy(
                        unblockingUserIds = success.unblockingUserIds - userId,
                        errorMessage = result.message,
                    )
                    UiResult.Loading -> success
                }
            }
        }
    }
}
