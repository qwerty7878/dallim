package com.dallim.app.my

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.onboarding.profile.avatarOptions
import com.dallim.network.user.UserMeResponseBody
import com.dallim.ui.components.DallimBottomNavigation
import com.dallim.ui.components.DallimCard
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimSecondaryButton
import com.dallim.ui.components.DallimTab
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-42 마이 (docs/01-feature-spec.md §1.5) — 아바타/닉네임/총 러닝 횟수/총 거리 조회 + 로그아웃.
 * 홈(S-10)과 마찬가지로 오직 하단 탭바로만 진입하는 최상위 화면이라 상단 뒤로가기 버튼은 두지
 * 않는다. 닉네임/아바타 수정 기능은 없다(스코프 밖 — `PATCH /users/me`가 SPEC에 없음).
 */
@Composable
fun MyRoute(
    onTabSelected: (DallimTab) -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                MyNavigationEvent.LoggedOut -> onLoggedOut()
            }
        }
    }

    MyScreen(
        uiState = uiState,
        onTabSelected = onTabSelected,
        onLogoutClick = viewModel::onLogoutClick,
        onRetryClick = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun MyScreen(
    uiState: MyUiState,
    onTabSelected: (DallimTab) -> Unit,
    onLogoutClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = DallimColors.Background,
        bottomBar = {
            DallimBottomNavigation(selectedTab = DallimTab.MY, onTabSelected = onTabSelected)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DallimColors.Background)
                .padding(innerPadding),
        ) {
            Text(
                text = "마이",
                style = DallimTypography.Title1,
                color = DallimColors.TextPrimary,
                modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md),
            )

            when (uiState) {
                is MyUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
                is MyUiState.Error -> DallimErrorState(
                    title = "프로필을 불러오지 못했어요",
                    description = uiState.message,
                    onRetry = onRetryClick,
                    modifier = Modifier.weight(1f),
                )
                is MyUiState.Success -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.ScreenHorizontal),
                ) {
                    ProfileCard(user = uiState.user)

                    DallimSecondaryButton(
                        text = if (uiState.isLoggingOut) "로그아웃 중…" else "로그아웃",
                        onClick = onLogoutClick,
                        enabled = !uiState.isLoggingOut,
                        modifier = Modifier.padding(top = Spacing.xl),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(user: UserMeResponseBody) {
    DallimCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarBadge(avatarId = user.avatarId)
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(text = user.nickname, style = DallimTypography.Title1, color = DallimColors.TextPrimary)
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.md),
            color = DallimColors.Border,
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem(label = "총 러닝 횟수", value = "${user.totalRuns}회", modifier = Modifier.weight(1f))
            StatItem(label = "총 거리", value = "%.1fkm".format(user.totalDistanceKm), modifier = Modifier.weight(1f))
        }
    }
}

/**
 * 아바타는 문자열 ID([UserMeResponseBody.avatarId])라 실물 이미지 에셋이 없다 — S-04(프로필
 * 설정)와 동일하게 [avatarOptions]의 벡터 아이콘으로 표시한다(이모지 아바타 절대 금지,
 * docs/04-ui-guide.md §7). 알 수 없는 avatarId가 오면 첫 옵션으로 폴백한다.
 */
@Composable
private fun AvatarBadge(avatarId: String) {
    val avatar = avatarOptions.firstOrNull { it.id == avatarId } ?: avatarOptions.first()
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(DallimColors.PrimaryLight),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = avatar.icon,
            contentDescription = null,
            tint = DallimColors.Primary,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = value, style = DallimTypography.Title2, color = DallimColors.TextPrimary)
        Text(
            text = label,
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun MyScreenPreview() {
    DallimTheme {
        MyScreen(
            uiState = MyUiState.Success(
                user = UserMeResponseBody(
                    userId = "user_001",
                    nickname = "달림이",
                    avatarId = "avatar_03",
                    runningExperience = "MONTHS_3_TO_12",
                    comfortablePace = "PACE_6_7",
                    totalRuns = 12,
                    totalDistanceKm = 48.7,
                ),
            ),
            onTabSelected = {},
            onLogoutClick = {},
            onRetryClick = {},
        )
    }
}
