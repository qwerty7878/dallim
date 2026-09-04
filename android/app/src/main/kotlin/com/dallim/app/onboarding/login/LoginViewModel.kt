package com.dallim.app.onboarding.login

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.push.DeviceTokenRegistrar
import com.dallim.network.auth.AuthApi
import com.dallim.network.auth.AuthResponseBody
import com.dallim.network.auth.GoogleLoginRequest
import com.dallim.network.auth.TokenProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * S-02 로그인 다음 화면 — [isNewUser]에 따라 갈린다: 신규 유저는 아직 약관/프로필 설정을
 * 마치지 않았으므로 S-03(약관동의)부터 온보딩을 이어가고, 기존 유저는 이미 온보딩을 마쳤으므로
 * 바로 홈으로 보낸다. (docs/01-feature-spec.md 1.1 "3가지 경로 모두 동일한 온보딩 플로우
 * (S-03~S-06)로 합류" 규칙은 3개 로그인 수단 전체에 대해 온보딩 진입점이 통일된다는 뜻이며,
 * 이미 온보딩을 마친 재방문 유저까지 다시 약관 동의를 태우라는 뜻은 아니라고 판단해
 * `isNewUser` 플래그로 분기했다.)
 */
sealed interface LoginNavigationEvent {
    data object GoToTerms : LoginNavigationEvent
    data object GoToHome : LoginNavigationEvent
    data object GoToSignup : LoginNavigationEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val tokenProvider: TokenProvider,
    private val socialLoginLauncher: SocialLoginLauncher,
    private val deviceTokenRegistrar: DeviceTokenRegistrar,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<LoginNavigationEvent>()
    val navigationEvents: SharedFlow<LoginNavigationEvent> = _navigationEvents.asSharedFlow()

    fun onGoogleClick(activity: Activity) {
        viewModelScope.launch {
            _uiState.value = LoginUiState(isLoading = true)
            socialLoginLauncher.launchGoogleSignIn(activity)
                .onSuccess { idToken ->
                    handleAuthResponse { authApi.loginWithGoogle(GoogleLoginRequest(idToken)) }
                }
                .onFailure { showError(it.message ?: "Google 로그인에 실패했어요.") }
        }
    }

    fun onEmailStartClick() {
        viewModelScope.launch { _navigationEvents.emit(LoginNavigationEvent.GoToSignup) }
    }

    private suspend fun handleAuthResponse(call: suspend () -> Response<com.dallim.network.common.ApiResponse<AuthResponseBody>>) {
        runCatching { call() }
            .onSuccess { response ->
                val body = response.body()
                val data = body?.data
                if (response.isSuccessful && body?.success == true && data != null) {
                    tokenProvider.saveTokens(data.accessToken, data.refreshToken)
                    // 로그인 성공 직후 FCM 토큰 등록 (docs/01-feature-spec.md §1.7 2단계).
                    viewModelScope.launch { deviceTokenRegistrar.registerCurrentToken() }
                    _uiState.value = LoginUiState(isLoading = false)
                    _navigationEvents.emit(
                        if (data.isNewUser) LoginNavigationEvent.GoToTerms else LoginNavigationEvent.GoToHome,
                    )
                } else {
                    showError(body?.error?.message ?: "로그인에 실패했어요. 다시 시도해주세요.")
                }
            }
            .onFailure { showError(it.message ?: "네트워크 오류가 발생했어요.") }
    }

    private fun showError(message: String) {
        _uiState.value = LoginUiState(isLoading = false, errorMessage = message)
    }
}
