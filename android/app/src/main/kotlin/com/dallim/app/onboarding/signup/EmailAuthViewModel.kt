package com.dallim.app.onboarding.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.network.auth.AuthApi
import com.dallim.network.auth.EmailLoginRequest
import com.dallim.network.auth.EmailSignupRequest
import com.dallim.network.auth.TokenProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S-02b 일반 회원가입/로그인.
 *
 * docs/01-feature-spec.md 1.1은 S-02b를 "일반 회원가입 폼"으로만 정의하지만, docs/02-api-spec.md
 * 1장에는 `POST /auth/signup`과 `POST /auth/login` 둘 다 있고, S-02("이메일로 시작하기")에서
 * 이 화면으로 들어오는 유저가 신규인지 기존 이메일 계정 보유자인지 이 시점엔 알 수 없다.
 * 별도 화면 ID가 스펙에 없으므로 이 화면 안에서 가입/로그인 모드를 토글하는 방식으로 두 API를
 * 모두 지원한다 — SPEC을 벗어난 새 화면을 만들지 않으면서 두 엔드포인트를 모두 연결하기 위한
 * 실용적 선택.
 */
enum class EmailAuthMode { SIGNUP, LOGIN }

data class EmailAuthUiState(
    val mode: EmailAuthMode = EmailAuthMode.SIGNUP,
    val email: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    /** docs/02-api-spec.md 1장: 이메일 형식. */
    val isEmailValid: Boolean
        get() = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

    /** docs/02-api-spec.md 1장: 8자 이상 + 영문/숫자 조합. */
    val isPasswordValid: Boolean
        get() = password.length >= 8 && password.any { it.isLetter() } && password.any { it.isDigit() }

    val isPasswordConfirmValid: Boolean
        get() = mode == EmailAuthMode.LOGIN || password == passwordConfirm

    val emailError: String?
        get() = if (email.isNotEmpty() && !isEmailValid) "이메일 형식을 확인해주세요." else null

    val passwordError: String?
        get() = if (password.isNotEmpty() && !isPasswordValid) "8자 이상, 영문/숫자를 조합해주세요." else null

    val passwordConfirmError: String?
        get() = if (mode == EmailAuthMode.SIGNUP && passwordConfirm.isNotEmpty() && !isPasswordConfirmValid) {
            "비밀번호가 일치하지 않아요."
        } else {
            null
        }

    val canSubmit: Boolean
        get() = isEmailValid && isPasswordValid && isPasswordConfirmValid && !isLoading
}

sealed interface EmailAuthNavigationEvent {
    data object GoToTerms : EmailAuthNavigationEvent
    data object GoToHome : EmailAuthNavigationEvent
}

@HiltViewModel
class EmailAuthViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val tokenProvider: TokenProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmailAuthUiState())
    val uiState: StateFlow<EmailAuthUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<EmailAuthNavigationEvent>()
    val navigationEvents: SharedFlow<EmailAuthNavigationEvent> = _navigationEvents.asSharedFlow()

    fun onModeToggle() {
        _uiState.value = _uiState.value.copy(
            mode = if (_uiState.value.mode == EmailAuthMode.SIGNUP) EmailAuthMode.LOGIN else EmailAuthMode.SIGNUP,
            errorMessage = null,
        )
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun onPasswordConfirmChange(value: String) {
        _uiState.value = _uiState.value.copy(passwordConfirm = value, errorMessage = null)
    }

    fun onSubmit() {
        val state = _uiState.value
        if (!state.canSubmit) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)
            runCatching {
                if (state.mode == EmailAuthMode.SIGNUP) {
                    authApi.signup(EmailSignupRequest(state.email, state.password))
                } else {
                    authApi.login(EmailLoginRequest(state.email, state.password))
                }
            }.onSuccess { response ->
                val body = response.body()
                val data = body?.data
                if (response.isSuccessful && body?.success == true && data != null) {
                    tokenProvider.saveTokens(data.accessToken, data.refreshToken)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _navigationEvents.emit(
                        if (data.isNewUser) EmailAuthNavigationEvent.GoToTerms else EmailAuthNavigationEvent.GoToHome,
                    )
                } else {
                    val message = when (response.code()) {
                        409 -> "이미 가입된 이메일이에요."
                        401 -> "이메일 또는 비밀번호가 올바르지 않아요."
                        else -> body?.error?.message ?: "요청에 실패했어요. 다시 시도해주세요."
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = message)
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = it.message ?: "네트워크 오류가 발생했어요.")
            }
        }
    }
}
