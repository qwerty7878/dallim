package com.dallim.app.onboarding.login

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
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
import com.dallim.app.onboarding.OnboardingArt
import com.dallim.app.onboarding.OnboardingMapArt
import com.dallim.ui.components.DallimMark
import com.dallim.ui.components.DallimSecondaryButton
import com.dallim.ui.theme.DallimTypography
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
        Row(
            modifier = Modifier.padding(top = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DallimMark(modifier = Modifier.size(28.dp))
            Text(
                text = "달림",
                style = DallimTypography.Title2,
                color = DallimColors.TextPrimary,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            // 앱이 하는 일을 그대로 보여준다 — 실제 지도 위에 그려진 하트 코스.
            OnboardingMapArt(OnboardingArt.Heart, Modifier.aspectRatio(1f))
            Text(
                text = "달리면서\n그림을 그려요",
                style = DallimTypography.Title1,
                color = DallimColors.TextPrimary,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            Text(
                text = "실제 도로 위, GPS 궤적으로 완성되는 나만의 러닝 코스",
                style = DallimTypography.Body,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = DallimTypography.Caption,
                color = DallimColors.Error,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }

        DallimPrimaryButton(text = "Google로 계속하기", onClick = onGoogleClick, enabled = !isLoading)
        DallimSecondaryButton(
            text = "이메일로 계속하기",
            onClick = onEmailStartClick,
            enabled = !isLoading,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.lg),
        )
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
