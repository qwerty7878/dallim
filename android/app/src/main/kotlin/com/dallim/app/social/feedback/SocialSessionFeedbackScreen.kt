package com.dallim.app.social.feedback

import com.dallim.ui.icons.DallimIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.social.SocialAvatarBadge
import com.dallim.network.social.SocialFeedbackTags
import com.dallim.network.social.SocialSessionFeedbackTargetItem
import com.dallim.ui.components.DallimCard
import com.dallim.ui.components.DallimCheckboxRow
import com.dallim.ui.components.DallimEmptyState
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimFilterChip
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.components.DallimSnackbar
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-38 세션 종료 후 평가 (docs/02-api-spec.md 17.16) — 체크인한 상대를 한 명씩 카드로 넘기며
 * 긍정 태그(최대 3개, 별점 없음) + "다시 같이 뛰고 싶어요"를 평가한다.
 */
@Composable
fun SocialSessionFeedbackRoute(
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SocialSessionFeedbackViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SocialSessionFeedbackScreen(
        uiState = uiState,
        onBackClick = onDoneClick,
        onRetryClick = viewModel::load,
        onTagToggle = viewModel::onTagToggle,
        onWantsToRunAgainChange = viewModel::onWantsToRunAgainChange,
        onSubmitClick = viewModel::onSubmitClick,
        onSkipClick = viewModel::onSkipClick,
        onDoneClick = onDoneClick,
        onMateEstablishedMessageShown = viewModel::onMateEstablishedMessageShown,
        modifier = modifier,
    )
}

@Composable
private fun SocialSessionFeedbackScreen(
    uiState: SocialSessionFeedbackUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onTagToggle: (String) -> Unit,
    onWantsToRunAgainChange: (Boolean) -> Unit,
    onSubmitClick: () -> Unit,
    onSkipClick: () -> Unit,
    onDoneClick: () -> Unit,
    onMateEstablishedMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val mateMessage = (uiState as? SocialSessionFeedbackUiState.Success)?.mateEstablishedMessage

    LaunchedEffect(mateMessage) {
        val message = mateMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMateEstablishedMessageShown()
    }

    Scaffold(
        modifier = modifier,
        containerColor = DallimColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) { DallimSnackbar(it) } },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DallimColors.Background)
                .padding(innerPadding)
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = DallimIcons.ArrowLeft, contentDescription = "뒤로가기", tint = DallimColors.TextPrimary)
                }
                Text(text = "함께 달린 사람 평가", style = DallimTypography.Title2, color = DallimColors.TextPrimary)
            }

            when (uiState) {
                SocialSessionFeedbackUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
                is SocialSessionFeedbackUiState.Error -> DallimErrorState(
                    title = "평가 대상을 불러오지 못했어요",
                    description = uiState.message,
                    onRetry = onRetryClick,
                    modifier = Modifier.weight(1f),
                )
                is SocialSessionFeedbackUiState.Success -> {
                    if (uiState.targets.isEmpty()) {
                        DallimEmptyState(
                            title = "함께 체크인한 사람이 없어요",
                            description = "체크인한 참가자끼리만 서로 평가할 수 있어요.",
                            actionText = "돌아가기",
                            onActionClick = onDoneClick,
                            modifier = Modifier.weight(1f),
                        )
                    } else if (uiState.isAllDone) {
                        DallimEmptyState(
                            title = "평가를 모두 마쳤어요",
                            description = "함께 달려줘서 고마워요!",
                            actionText = "완료",
                            onActionClick = onDoneClick,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        FeedbackTargetContent(
                            state = uiState,
                            onTagToggle = onTagToggle,
                            onWantsToRunAgainChange = onWantsToRunAgainChange,
                            onSubmitClick = onSubmitClick,
                            onSkipClick = onSkipClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackTargetContent(
    state: SocialSessionFeedbackUiState.Success,
    onTagToggle: (String) -> Unit,
    onWantsToRunAgainChange: (Boolean) -> Unit,
    onSubmitClick: () -> Unit,
    onSkipClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = state.currentTarget ?: return
    Column(modifier = modifier.fillMaxSize().padding(horizontal = Spacing.ScreenHorizontal)) {
        Text(
            text = "${state.currentIndex + 1} / ${state.targets.size}",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.sm),
        )

        DallimCard(modifier = Modifier.padding(top = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SocialAvatarBadge(avatarId = target.avatarId, size = 56.dp)
                Column(modifier = Modifier.padding(start = Spacing.md)) {
                    Text(text = target.nickname, style = DallimTypography.Title2, color = DallimColors.TextPrimary)
                    if (target.isHost) {
                        Text(text = "호스트", style = DallimTypography.Caption, color = DallimColors.Primary)
                    }
                }
            }
        }

        Text(
            text = "어떤 점이 좋았나요? (최대 ${SocialFeedbackTags.MAX_TAGS}개, 선택)",
            style = DallimTypography.Body,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SocialFeedbackTags.ALL.forEach { tag ->
                DallimFilterChip(
                    label = tag,
                    selected = tag in state.selectedTags,
                    onClick = { onTagToggle(tag) },
                )
            }
        }

        DallimCheckboxRow(
            checked = state.wantsToRunAgain,
            onCheckedChange = onWantsToRunAgainChange,
            label = "다시 같이 뛰고 싶어요",
            modifier = Modifier.padding(top = Spacing.lg),
        )

        if (state.submitErrorMessage != null) {
            Text(
                text = state.submitErrorMessage,
                style = DallimTypography.Caption,
                color = DallimColors.Error,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            DallimTextButton(text = "건너뛰기", onClick = onSkipClick, enabled = !state.isSubmitting)
        }
        DallimPrimaryButton(
            text = if (state.isSubmitting) "제출하는 중..." else "제출하고 다음으로",
            onClick = onSubmitClick,
            enabled = !state.isSubmitting,
            modifier = Modifier.padding(bottom = Spacing.lg),
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SocialSessionFeedbackScreenPreview() {
    DallimTheme {
        SocialSessionFeedbackScreen(
            uiState = SocialSessionFeedbackUiState.Success(
                targets = listOf(
                    SocialSessionFeedbackTargetItem(userId = "usr_1", nickname = "달림이", avatarId = "avatar_02", isHost = true),
                    SocialSessionFeedbackTargetItem(userId = "usr_2", nickname = "러너B", avatarId = "avatar_05", isHost = false),
                ),
                selectedTags = setOf(SocialFeedbackTags.ON_TIME, SocialFeedbackTags.SAFE),
                wantsToRunAgain = true,
            ),
            onBackClick = {},
            onRetryClick = {},
            onTagToggle = {},
            onWantsToRunAgainChange = {},
            onSubmitClick = {},
            onSkipClick = {},
            onDoneClick = {},
            onMateEstablishedMessageShown = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SocialSessionFeedbackScreenAllDonePreview() {
    DallimTheme {
        SocialSessionFeedbackScreen(
            uiState = SocialSessionFeedbackUiState.Success(
                targets = listOf(
                    SocialSessionFeedbackTargetItem(userId = "usr_1", nickname = "달림이", avatarId = "avatar_02", isHost = true),
                ),
                currentIndex = 1,
            ),
            onBackClick = {},
            onRetryClick = {},
            onTagToggle = {},
            onWantsToRunAgainChange = {},
            onSubmitClick = {},
            onSkipClick = {},
            onDoneClick = {},
            onMateEstablishedMessageShown = {},
        )
    }
}
