package com.dallim.app.onboarding.login

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.Spacing

/**
 * S-02 로그인 — Google 로그인 -> 구분선 -> [이메일로 시작하기](텍스트버튼). Kakao는 제외하기로
 * 결정되어 Google/이메일 2종만 지원한다 (백엔드 Kakao 연동은 이미 완성돼 있으나 이번 라운드는
 * Google부터 먼저 완성한다). Google Sign-In은 Credential Manager로 실제 연동돼 있다
 * ([RealSocialLoginLauncher] 참고).
 */
@Composable
fun LoginRoute(
    onNavigateTerms: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateSignup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                LoginNavigationEvent.GoToTerms -> onNavigateTerms()
                LoginNavigationEvent.GoToHome -> onNavigateHome()
                LoginNavigationEvent.GoToSignup -> onNavigateSignup()
            }
        }
    }

    LoginScreen(
        isLoading = uiState.isLoading,
        errorMessage = uiState.errorMessage,
        onGoogleClick = { viewModel.onGoogleClick(activity) },
        onEmailStartClick = viewModel::onEmailStartClick,
        modifier = modifier,
    )
}

/** Credential Manager의 getCredential()은 Activity Context를 요구한다 (system UI 앵커링). */
private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("LoginScreen은 Activity Context 내에서만 사용할 수 있어요.")
}

@Composable
private fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onGoogleClick: () -> Unit,
    onEmailStartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.ScreenHorizontal),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "달림과 함께\n달리며 그림을 그려요",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = DallimColors.TextPrimary,
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                fontSize = 13.sp,
                color = DallimColors.Error,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }

        // Google 브랜드 컬러가 DallimColors에 지정되어 있지 않아(디자인 시스템 §1) 브랜드 고유색을
        // 하드코딩하는 대신 기존 Primary 버튼 톤을 그대로 사용한다.
        DallimPrimaryButton(text = "Google로 시작하기", onClick = onGoogleClick, enabled = !isLoading)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f).height(1.dp).background(DallimColors.Border))
            Text(
                text = "또는",
                fontSize = 13.sp,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
            Box(modifier = Modifier.weight(1f).height(1.dp).background(DallimColors.Border))
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DallimTextButton(text = "이메일로 시작하기", onClick = onEmailStartClick)
        }

        Box(modifier = Modifier.height(Spacing.xl))
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    DallimTheme {
        LoginScreen(
            isLoading = false,
            errorMessage = null,
            onGoogleClick = {},
            onEmailStartClick = {},
        )
    }
}
