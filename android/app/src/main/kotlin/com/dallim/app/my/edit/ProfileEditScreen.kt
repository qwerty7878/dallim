package com.dallim.app.my.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.onboarding.profile.avatarOptions
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimTextField
import com.dallim.ui.components.SectionHeader
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * 프로필 수정 (2026-09-16 신규, 사용자 지시 — v1.3 SPEC 밖). 마이(S-42) 프로필 카드의 연필
 * 아이콘에서 진입. 닉네임 텍스트 필드(초기값 = 현재 닉네임) + 아바타 선택 그리드
 * ([avatarOptions], 온보딩 S-04와 동일 6종 재사용) — 저장은 `PATCH /users/me`.
 */
@Composable
fun ProfileEditRoute(
    onBackClick: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                ProfileEditNavigationEvent.Saved -> onSaved()
            }
        }
    }

    ProfileEditScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetryClick = viewModel::load,
        onNicknameChange = viewModel::onNicknameChange,
        onAvatarSelected = viewModel::onAvatarSelected,
        onSaveClick = viewModel::onSaveClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditScreen(
    uiState: ProfileEditUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onAvatarSelected: (String) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = DallimColors.TextPrimary,
                )
            }
            Text(text = "프로필 수정", style = DallimTypography.Title1, color = DallimColors.TextPrimary)
        }

        when (uiState) {
            ProfileEditUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
            is ProfileEditUiState.Error -> DallimErrorState(
                title = "프로필을 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            is ProfileEditUiState.Success -> Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.ScreenHorizontal),
            ) {
                SectionHeader(title = "닉네임", modifier = Modifier.padding(top = Spacing.lg))
                DallimTextField(
                    value = uiState.nickname,
                    onValueChange = onNicknameChange,
                    label = "닉네임",
                    errorText = uiState.nicknameError,
                )

                SectionHeader(title = "아바타", modifier = Modifier.padding(top = Spacing.xl))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    avatarOptions.forEach { avatar ->
                        AvatarGridItem(
                            icon = avatar.icon,
                            selected = uiState.selectedAvatarId == avatar.id,
                            onClick = { onAvatarSelected(avatar.id) },
                        )
                    }
                }

                if (uiState.generalError != null) {
                    Text(
                        text = uiState.generalError,
                        style = DallimTypography.Caption,
                        color = DallimColors.Error,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }

                DallimPrimaryButton(
                    text = if (uiState.isSaving) "저장하는 중..." else "저장",
                    onClick = onSaveClick,
                    enabled = uiState.canSave,
                    modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.xl),
                )
            }
        }
    }
}

@Composable
private fun AvatarGridItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .background(DallimColors.PrimaryLight, shape = CircleShape)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) DallimColors.Primary else DallimColors.Border,
                shape = CircleShape,
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = DallimColors.Primary, modifier = Modifier.size(28.dp))
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ProfileEditScreenPreview() {
    DallimTheme {
        ProfileEditScreen(
            uiState = ProfileEditUiState.Success(nickname = "달림이", selectedAvatarId = "avatar_03"),
            onBackClick = {},
            onRetryClick = {},
            onNicknameChange = {},
            onAvatarSelected = {},
            onSaveClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ProfileEditScreenNicknameErrorPreview() {
    DallimTheme {
        ProfileEditScreen(
            uiState = ProfileEditUiState.Success(
                nickname = "달림이",
                selectedAvatarId = "avatar_03",
                nicknameError = "이미 사용 중인 닉네임이에요.",
            ),
            onBackClick = {},
            onRetryClick = {},
            onNicknameChange = {},
            onAvatarSelected = {},
            onSaveClick = {},
        )
    }
}
