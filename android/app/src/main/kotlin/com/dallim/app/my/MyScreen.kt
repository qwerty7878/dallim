package com.dallim.app.my

import com.dallim.ui.icons.DallimIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.onboarding.profile.ComfortablePace
import com.dallim.app.onboarding.profile.RunningExperience
import com.dallim.app.onboarding.profile.avatarOptions
import com.dallim.network.user.UserMeResponseBody
import com.dallim.ui.components.DallimBottomNavigation
import com.dallim.ui.components.DallimCard
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.components.DallimTab
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-42 마이 (docs/01-feature-spec.md §1.5) — 아바타/닉네임/총 러닝 횟수/총 거리/러닝 경력/편안한
 * 페이스 조회 + 로그아웃. 러닝 경력·페이스는 `GET /users/me` 응답에 자유형식 문자열로 오는 값을
 * 온보딩(S-04, [RunningExperience]/[ComfortablePace])의 한글 라벨 매핑으로 표시한다 — 값이
 * 비어있으면(온보딩 프로필 설정 미완료 계정) 해당 행을 "미설정"으로 대체한다.
 * 홈(S-10)과 마찬가지로 오직 하단 탭바로만 진입하는 최상위 화면이라 상단 뒤로가기 버튼은 두지
 * 않는다. 프로필 카드의 연필 아이콘(2026-09-16 신규, 사용자 지시 — v1.3 SPEC 밖)이 닉네임/아바타
 * 수정 화면([com.dallim.app.my.edit.ProfileEditRoute], `PATCH /users/me`)으로 보낸다.
 *
 * S-91(완주 메달 선반) 진입점을 카드 하나로 둔다(2026-09-06 신규) — 새 탭을 만들지 않고 기존
 * 4탭 구조를 유지한다는 원칙에 따라, 마이 안에서만 진입 가능하게 한다.
 *
 * 달림북(S-40) 진입점도 카드 하나로 둔다(2026-09-16 재편) — 바텀탭이 UI/UX상 4~5개가
 * 적정하다는 사용자 지적과 "달림북은 내 기록이니 마이 안에 있는 게 자연스럽다"는 지시에 따라
 * 최상위 탭에서 빼서 [DallimbookEntryCard]로 옮겼다.
 */
@Composable
fun MyRoute(
    onTabSelected: (DallimTab) -> Unit,
    onEditProfileClick: () -> Unit,
    onDallimbookClick: () -> Unit,
    onMedalShelfClick: () -> Unit,
    onRunningMatesClick: () -> Unit,
    onBlockedUsersClick: () -> Unit,
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

    // 프로필 수정(신규 화면)에서 닉네임/아바타를 바꾸고 돌아왔을 때 반영하는 새로고침 — 이 화면은
    // 탭 루트라 SavedStateHandle 플래그 릴레이 대신 RESUME마다 다시 불러오는 더 단순한 방식을
    // 쓴다(app/discover/DiscoverScreen.kt의 소셜 세그먼트와 동일 패턴, 실측으로 검증된 방식).
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    MyScreen(
        uiState = uiState,
        onTabSelected = onTabSelected,
        onEditProfileClick = onEditProfileClick,
        onDallimbookClick = onDallimbookClick,
        onMedalShelfClick = onMedalShelfClick,
        onRunningMatesClick = onRunningMatesClick,
        onBlockedUsersClick = onBlockedUsersClick,
        onLogoutClick = viewModel::onLogoutClick,
        onRetryClick = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun MyScreen(
    uiState: MyUiState,
    onTabSelected: (DallimTab) -> Unit,
    onEditProfileClick: () -> Unit,
    onDallimbookClick: () -> Unit,
    onMedalShelfClick: () -> Unit,
    onRunningMatesClick: () -> Unit,
    onBlockedUsersClick: () -> Unit,
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
                        .verticalScroll(rememberScrollState()),
                ) {
                    ProfileHeader(
                        user = uiState.user,
                        onEditClick = onEditProfileClick,
                        modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal),
                    )

                    // 2026-10-05: 메뉴 4줄에 "~해보세요" 설명문을 하나씩 달아둔 것이 이 화면이 가장
                    // "자동 생성된 앱"처럼 보이던 이유였다(네 줄이 전부 같은 문장 구조). 제목만 남기고
                    // 회색 카드 껍데기도 벗겨 구분선 목록으로 폈다(docs/04-ui-guide.md §4).
                    Column(modifier = Modifier.padding(top = Spacing.xl)) {
                        MenuRow(DallimIcons.BookOpen, "달림북", onDallimbookClick)
                        MenuRow(DallimIcons.Trophy, "완주 메달 선반", onMedalShelfClick)
                        MenuRow(DallimIcons.Users, "러닝메이트", onRunningMatesClick)
                        MenuRow(DallimIcons.Ban, "차단 관리", onBlockedUsersClick)
                    }

                    DallimTextButton(
                        text = if (uiState.isLoggingOut) "로그아웃 중…" else "로그아웃",
                        onClick = onLogoutClick,
                        enabled = !uiState.isLoggingOut,
                        modifier = Modifier.padding(top = Spacing.lg).align(Alignment.CenterHorizontally),
                    )

                    Box(modifier = Modifier.padding(bottom = Spacing.xxl))
                }
            }
        }
    }
}

/**
 * 2026-10-05: 회색 카드 안에 아바타·닉네임·숫자를 전부 욱여넣던 [DallimCard]를 없애고, 화면 상단
 * 자체를 프로필로 쓴다 — 누적 거리/횟수를 카드 안 작은 글씨가 아니라 화면에서 가장 큰 숫자로 둔다.
 */
@Composable
private fun ProfileHeader(user: UserMeResponseBody, onEditClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarBadge(avatarId = user.avatarId)
            Column(modifier = Modifier.padding(start = Spacing.md).weight(1f)) {
                Text(text = user.nickname, style = DallimTypography.Title1, color = DallimColors.TextPrimary)
                // 경력/편안한 페이스는 통계가 아니라 설정값이라 닉네임 아래 한 줄 보조 텍스트로 내렸다.
                val profileLine = listOfNotNull(
                    user.runningExperience.toRunningExperienceLabel(),
                    user.comfortablePace.toComfortablePaceLabel()?.let { "페이스 ${it.replace(" ~ ", "~")}" },
                ).joinToString(" · ")
                if (profileLine.isNotEmpty()) {
                    Text(
                        text = profileLine,
                        style = DallimTypography.Caption,
                        color = DallimColors.TextSecondary,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = DallimIcons.Pencil,
                    contentDescription = "프로필 수정",
                    tint = DallimColors.TextSecondary,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl)) {
            StatItem(label = "달린 횟수", value = "${user.totalRuns}", unit = "회", modifier = Modifier.weight(1f))
            StatItem(label = "누적 거리", value = "%.1f".format(user.totalDistanceKm), unit = "km", modifier = Modifier.weight(1f))
        }
    }
}

/**
 * 온보딩(S-04)의 [RunningExperience]/[ComfortablePace] 한글 라벨 매핑을 재사용한다 — 새 매핑을
 * 중복 생성하지 않는다. 값이 빈 문자열이거나(온보딩 프로필 설정 미완료 계정) 알 수 없는 값이면
 * null을 반환해 호출부가 행을 숨기거나 대체 텍스트를 넣도록 한다.
 */
private fun String.toRunningExperienceLabel(): String? =
    takeIf { it.isNotBlank() }?.let { value -> RunningExperience.entries.firstOrNull { it.apiValue == value }?.label }

private fun String.toComfortablePaceLabel(): String? =
    takeIf { it.isNotBlank() }?.let { value -> ComfortablePace.entries.firstOrNull { it.apiValue == value }?.label }

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

/**
 * 마이 메뉴 한 줄 — [아이콘 | 제목 | ▸]. 진입점 목록: 달림북(S-40, 2026-09-16 탭에서 이동),
 * 완주 메달 선반(S-91), 러닝메이트(S-39, docs/02-api-spec.md 17.17), 차단 관리(18.2).
 *
 * 2026-10-05: 제목 아래 설명문("완주한 GPS 그림을 모아보세요" 류)을 전부 뺐다 — 네 줄이 같은
 * 문장 구조로 반복돼 화면이 설명서처럼 보였고, 메뉴 이름만으로 뜻이 충분히 전달된다.
 */
@Composable
private fun MenuRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DallimColors.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = title,
            style = DallimTypography.Title3,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(start = Spacing.md).weight(1f),
        )
        Icon(
            imageVector = DallimIcons.ChevronRight,
            contentDescription = null,
            tint = DallimColors.TextTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 숫자가 주인공 — 라벨은 숫자 아래 작게. */
@Composable
private fun StatItem(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = DallimTypography.Display, color = DallimColors.TextPrimary)
            Text(
                text = unit,
                style = DallimTypography.Title3,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.sm),
            )
        }
        Text(
            text = label,
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
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
            onEditProfileClick = {},
            onDallimbookClick = {},
            onMedalShelfClick = {},
            onRunningMatesClick = {},
            onBlockedUsersClick = {},
            onLogoutClick = {},
            onRetryClick = {},
        )
    }
}
