package com.dallim.app.onboarding.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimTextField
import com.dallim.ui.components.SectionHeader
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.Spacing

/** S-04 프로필 설정 — 닉네임 중복확인, 아바타 6종, 러닝경험/페이스/성별. */
@Composable
fun ProfileSetupRoute(
    onNavigatePermission: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                ProfileSetupNavigationEvent.GoToPermission -> onNavigatePermission()
            }
        }
    }

    ProfileSetupScreen(
        uiState = uiState,
        onNicknameChange = viewModel::onNicknameChange,
        onAvatarSelected = viewModel::onAvatarSelected,
        onRunningExperienceSelected = viewModel::onRunningExperienceSelected,
        onComfortablePaceSelected = viewModel::onComfortablePaceSelected,
        onGenderSelected = viewModel::onGenderSelected,
        onSubmit = viewModel::onSubmit,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSetupScreen(
    uiState: ProfileSetupUiState,
    onNicknameChange: (String) -> Unit,
    onAvatarSelected: (String) -> Unit,
    onRunningExperienceSelected: (RunningExperience) -> Unit,
    onComfortablePaceSelected: (ComfortablePace) -> Unit,
    onGenderSelected: (Gender) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.ScreenHorizontal),
    ) {
        Text(
            text = "프로필을 설정해주세요",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.xxl, bottom = Spacing.xl),
        )

        SectionHeader(title = "닉네임")
        DallimTextField(
            value = uiState.nickname,
            onValueChange = onNicknameChange,
            label = "닉네임",
            placeholder = "달림에서 사용할 닉네임",
            errorText = when (uiState.nicknameCheckState) {
                NicknameCheckState.TAKEN -> "이미 사용 중인 닉네임이에요."
                NicknameCheckState.ERROR -> "확인 중 오류가 발생했어요. 다시 시도해주세요."
                else -> null
            },
        )
        val statusText = when (uiState.nicknameCheckState) {
            NicknameCheckState.CHECKING -> "확인 중..."
            NicknameCheckState.AVAILABLE -> "사용할 수 있는 닉네임이에요."
            else -> null
        }
        if (statusText != null) {
            Text(
                text = statusText,
                fontSize = 13.sp,
                color = if (uiState.nicknameCheckState == NicknameCheckState.AVAILABLE) {
                    DallimColors.Success
                } else {
                    DallimColors.TextSecondary
                },
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        SectionHeader(title = "아바타", modifier = Modifier.padding(top = Spacing.xl))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(vertical = Spacing.xs),
        ) {
            items(avatarOptions) { avatar ->
                AvatarChoice(
                    avatar = avatar,
                    selected = uiState.selectedAvatarId == avatar.id,
                    onClick = { onAvatarSelected(avatar.id) },
                )
            }
        }

        SectionHeader(title = "러닝 경험", modifier = Modifier.padding(top = Spacing.xl))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RunningExperience.entries.forEach { option ->
                SelectableChip(
                    label = option.label,
                    selected = uiState.runningExperience == option,
                    onClick = { onRunningExperienceSelected(option) },
                )
            }
        }

        SectionHeader(title = "편안한 페이스", modifier = Modifier.padding(top = Spacing.xl))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ComfortablePace.entries.forEach { option ->
                SelectableChip(
                    label = option.label,
                    selected = uiState.comfortablePace == option,
                    onClick = { onComfortablePaceSelected(option) },
                )
            }
        }

        SectionHeader(title = "성별", modifier = Modifier.padding(top = Spacing.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Gender.entries.forEach { option ->
                SelectableChip(
                    label = option.label,
                    selected = uiState.gender == option,
                    onClick = { onGenderSelected(option) },
                )
            }
        }

        if (uiState.submitError != null) {
            Text(
                text = uiState.submitError,
                fontSize = 13.sp,
                color = DallimColors.Error,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }

        DallimPrimaryButton(
            text = "다음",
            onClick = onSubmit,
            enabled = uiState.canSubmit,
            modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.xl),
        )
    }
}

@Composable
private fun AvatarChoice(
    avatar: AvatarOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(DallimColors.PrimaryLight)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) DallimColors.Primary else DallimColors.Border,
                shape = CircleShape,
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = avatar.icon,
            contentDescription = null,
            tint = DallimColors.Primary,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) DallimColors.Primary else DallimColors.Surface)
            .border(
                width = 1.dp,
                color = if (selected) DallimColors.Primary else DallimColors.Border,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) androidx.compose.ui.graphics.Color.White else DallimColors.TextPrimary,
        )
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun ProfileSetupScreenPreview() {
    DallimTheme {
        ProfileSetupScreen(
            uiState = ProfileSetupUiState(
                nickname = "달리는고래",
                nicknameCheckState = NicknameCheckState.AVAILABLE,
                selectedAvatarId = "avatar_03",
                runningExperience = RunningExperience.UNDER_3_MONTHS,
                comfortablePace = ComfortablePace.PACE_6_7,
                gender = null,
            ),
            onNicknameChange = {},
            onAvatarSelected = {},
            onRunningExperienceSelected = {},
            onComfortablePaceSelected = {},
            onGenderSelected = {},
            onSubmit = {},
        )
    }
}
