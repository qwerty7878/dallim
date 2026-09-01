package com.dallim.app.onboarding.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.components.DallimTextField
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.Spacing

/**
 * S-02b 일반 회원가입 — docs/01-feature-spec.md 1.1: 이메일/비밀번호/비밀번호확인, 클라이언트
 * 검증(이메일 형식, 비밀번호 8자+영문/숫자, 비밀번호 확인 일치). 가입 성공 -> S-03.
 * 모드 토글로 기존 이메일 계정의 로그인도 이 화면에서 처리한다 — [EmailAuthViewModel] 문서 참고.
 */
@Composable
fun SignupRoute(
    onNavigateTerms: () -> Unit,
    onNavigateHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmailAuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                EmailAuthNavigationEvent.GoToTerms -> onNavigateTerms()
                EmailAuthNavigationEvent.GoToHome -> onNavigateHome()
            }
        }
    }

    SignupScreen(
        uiState = uiState,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onPasswordConfirmChange = viewModel::onPasswordConfirmChange,
        onModeToggle = viewModel::onModeToggle,
        onSubmit = viewModel::onSubmit,
        modifier = modifier,
    )
}

@Composable
private fun SignupScreen(
    uiState: EmailAuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirmChange: (String) -> Unit,
    onModeToggle: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSignup = uiState.mode == EmailAuthMode.SIGNUP

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.ScreenHorizontal),
    ) {
        Text(
            text = if (isSignup) "이메일로 시작하기" else "이메일로 로그인",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.xxl, bottom = Spacing.xl),
        )

        DallimTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            label = "이메일",
            placeholder = "you@example.com",
            keyboardType = KeyboardType.Email,
            errorText = uiState.emailError,
        )

        Box(modifier = Modifier.padding(top = Spacing.md)) {
            DallimTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                label = "비밀번호",
                placeholder = "8자 이상, 영문/숫자 조합",
                isPassword = true,
                keyboardType = KeyboardType.Password,
                errorText = uiState.passwordError,
            )
        }

        if (isSignup) {
            Box(modifier = Modifier.padding(top = Spacing.md)) {
                DallimTextField(
                    value = uiState.passwordConfirm,
                    onValueChange = onPasswordConfirmChange,
                    label = "비밀번호 확인",
                    placeholder = "비밀번호를 다시 입력해주세요",
                    isPassword = true,
                    keyboardType = KeyboardType.Password,
                    errorText = uiState.passwordConfirmError,
                )
            }
        }

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                fontSize = 13.sp,
                color = DallimColors.Error,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
            DallimPrimaryButton(
                text = if (isSignup) "가입하기" else "로그인",
                onClick = onSubmit,
                enabled = uiState.canSubmit,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            DallimTextButton(
                text = if (isSignup) "이미 계정이 있으신가요? 로그인" else "처음이신가요? 가입하기",
                onClick = onModeToggle,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignupScreenPreview() {
    DallimTheme {
        SignupScreen(
            uiState = EmailAuthUiState(),
            onEmailChange = {},
            onPasswordChange = {},
            onPasswordConfirmChange = {},
            onModeToggle = {},
            onSubmit = {},
        )
    }
}
