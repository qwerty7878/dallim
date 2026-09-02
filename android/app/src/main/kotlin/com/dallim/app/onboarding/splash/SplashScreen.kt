package com.dallim.app.onboarding.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.ui.components.DallimMark
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme

/**
 * S-00 스플래시 — docs/01-feature-spec.md 1.1. Pure side-effect screen: solid [DallimColors.Background]
 * behind the app's brand mark ([DallimMark], the same "러닝화 + 위치 핀" silhouette as the launcher
 * icon), shown briefly while [SplashViewModel] resolves where to go, then fires [onNavigateHome] /
 * [onNavigateCarousel] exactly once.
 *
 * 2026-08-25 결정 변경(docs/03-design-system.md 1.2 스플래시 항목): 화면 전체를 그라디언트로
 * 채우고 "달림" 텍스트를 띄우던 이전 방식은 폐기 — 배민·토스 등 국내 앱 관례를 따라 단색 배경
 * 위에 앱 아이콘 마크만 짧게 노출한다.
 */
@Composable
fun SplashRoute(
    onNavigateHome: () -> Unit,
    onNavigateCarousel: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()

    LaunchedEffect(destination) {
        when (destination) {
            SplashDestination.Home -> onNavigateHome()
            SplashDestination.OnboardingCarousel -> onNavigateCarousel()
            SplashDestination.Loading -> Unit
        }
    }

    SplashScreen()
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DallimColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        DallimMark(modifier = Modifier.size(112.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashScreenPreview() {
    DallimTheme {
        SplashScreen()
    }
}
