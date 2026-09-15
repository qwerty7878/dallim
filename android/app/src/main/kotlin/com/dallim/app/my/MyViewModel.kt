package com.dallim.app.my

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.network.auth.AuthApi
import com.dallim.network.auth.TokenProvider
import com.dallim.network.user.UserApi
import com.dallim.network.user.UserMeResponseBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MyUiState {
    data object Loading : MyUiState

    data class Success(
        val user: UserMeResponseBody,
        val isLoggingOut: Boolean = false,
    ) : MyUiState

    data class Error(val message: String) : MyUiState
}

/** S-42가 로그아웃 완료 시 딱 한 번 쏘는 이벤트 — NavHost가 이걸 받아 백스택을 비우고 S-02로 보낸다. */
sealed interface MyNavigationEvent {
    data object LoggedOut : MyNavigationEvent
}

/**
 * S-42 마이 — 아바타/닉네임/총 러닝 횟수/총 거리 조회 + 로그아웃 (docs/01-feature-spec.md §1.5,
 * `GET /users/me`, `POST /auth/logout`). 닉네임/아바타 수정 자체는 별도 화면
 * ([com.dallim.app.my.edit.ProfileEditViewModel], `PATCH /users/me`)이 담당하고, 이 화면은 그
 * 화면에서 돌아왔을 때(RESUME) [load]를 다시 호출해 갱신된 값을 반영하기만 한다.
 */
@HiltViewModel
class MyViewModel @Inject constructor(
    private val userApi: UserApi,
    private val authApi: AuthApi,
    private val tokenProvider: TokenProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MyUiState>(MyUiState.Loading)
    val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<MyNavigationEvent>()
    val navigationEvents: SharedFlow<MyNavigationEvent> = _navigationEvents.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = MyUiState.Loading
            _uiState.value = when (val result = safeApiCall { userApi.getMe() }) {
                is UiResult.Success -> MyUiState.Success(user = result.data)
                is UiResult.Error -> MyUiState.Error(result.message)
                UiResult.Loading -> MyUiState.Loading
            }
        }
    }

    /**
     * `POST /auth/logout`은 best-effort다 — 서버 세션 폐기가 실패해도 클라이언트는 그대로
     * 진행한다. 로컬 토큰([TokenProvider.clearTokens]) 삭제가 실제 로그아웃의 핵심이다
     * (docs/01-feature-spec.md §1.5).
     */
    fun onLogoutClick() {
        val current = _uiState.value
        if (current is MyUiState.Success && current.isLoggingOut) return
        viewModelScope.launch {
            if (current is MyUiState.Success) {
                _uiState.value = current.copy(isLoggingOut = true)
            }
            runCatching { authApi.logout() }
            tokenProvider.clearTokens()
            _navigationEvents.emit(MyNavigationEvent.LoggedOut)
        }
    }
}
