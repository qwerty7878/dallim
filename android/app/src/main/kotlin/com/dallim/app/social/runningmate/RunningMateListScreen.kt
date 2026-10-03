package com.dallim.app.social.runningmate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.social.SocialAvatarBadge
import com.dallim.app.social.SocialSessionFormat
import com.dallim.network.social.RunningMateItem
import com.dallim.ui.components.DallimCard
import com.dallim.ui.components.DallimEmptyState
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimTopBar
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.components.DallimSnackbar
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-39 Running Mate 목록 (docs/02-api-spec.md 17.17) — 마이(S-42)에서 진입. 해제는 조용히
 * 단방향(상대 알림 없음)이라 확인 다이얼로그만 두고 별다른 경고 문구는 두지 않는다(SPEC에 UX
 * 문구 지정 없음, 과설계 금지 — 작업 브리핑 참고).
 */
@Composable
fun RunningMateListRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RunningMateListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RunningMateListScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetryClick = viewModel::load,
        onRemoveClick = viewModel::onRemoveClick,
        onRemoveDialogDismiss = viewModel::onRemoveDialogDismiss,
        onRemoveConfirm = viewModel::onRemoveConfirm,
        onErrorMessageShown = viewModel::onErrorMessageShown,
        modifier = modifier,
    )
}

@Composable
private fun RunningMateListScreen(
    uiState: RunningMateListUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onRemoveClick: (String) -> Unit,
    onRemoveDialogDismiss: () -> Unit,
    onRemoveConfirm: () -> Unit,
    onErrorMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = (uiState as? RunningMateListUiState.Success)?.errorMessage

    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onErrorMessageShown()
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
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            DallimTopBar(title = "러닝메이트", onBackClick = onBackClick)

            when (uiState) {
                RunningMateListUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
                is RunningMateListUiState.Error -> DallimErrorState(
                    title = "러닝메이트를 불러오지 못했어요",
                    description = uiState.message,
                    onRetry = onRetryClick,
                    modifier = Modifier.weight(1f),
                )
                is RunningMateListUiState.Success -> if (uiState.items.isEmpty()) {
                    DallimEmptyState(
                        title = "아직 러닝메이트가 없어요",
                        description = "소셜 세션에서 함께 달리고 서로 \"다시 같이 뛰고 싶어요\"를 남기면 메이트가 돼요.",
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = Spacing.ScreenHorizontal,
                            end = Spacing.ScreenHorizontal,
                            top = Spacing.sm,
                            bottom = Spacing.xl,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.ListItemGap),
                    ) {
                        items(uiState.items, key = { it.userId }) { item ->
                            RunningMateRow(
                                item = item,
                                isRemoving = uiState.removingUserId == item.userId,
                                onRemoveClick = { onRemoveClick(item.userId) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState is RunningMateListUiState.Success && uiState.pendingRemoveUserId != null) {
        val target = uiState.items.firstOrNull { it.userId == uiState.pendingRemoveUserId }
        RemoveMateConfirmDialog(
            nickname = target?.nickname ?: "",
            onDismiss = onRemoveDialogDismiss,
            onConfirm = onRemoveConfirm,
        )
    }
}

@Composable
private fun RunningMateRow(item: RunningMateItem, isRemoving: Boolean, onRemoveClick: () -> Unit, modifier: Modifier = Modifier) {
    DallimCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SocialAvatarBadge(avatarId = item.avatarId, size = 48.dp)
            Column(modifier = Modifier.padding(start = Spacing.md).weight(1f)) {
                Text(text = item.nickname, style = DallimTypography.Body, color = DallimColors.TextPrimary)
                Text(
                    text = "같이 달린 횟수 ${item.runTogetherCount}회 · 최근 ${SocialSessionFormat.displayAppliedAt(item.lastRunTogetherAt)}",
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            DallimTextButton(text = if (isRemoving) "해제 중..." else "해제", onClick = onRemoveClick, enabled = !isRemoving)
        }
    }
}

@Composable
private fun RemoveMateConfirmDialog(nickname: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = DallimShapes.CardCorner, color = DallimColors.Surface) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(text = "러닝메이트 해제", style = DallimTypography.Title2, color = DallimColors.TextPrimary)
                Text(
                    text = "${nickname}님과의 러닝메이트를 해제할까요?",
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                ) {
                    DallimTextButton(text = "취소", onClick = onDismiss)
                    DallimTextButton(text = "해제하기", onClick = onConfirm)
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun RunningMateListScreenPreview() {
    DallimTheme {
        RunningMateListScreen(
            uiState = RunningMateListUiState.Success(
                items = listOf(
                    RunningMateItem(
                        userId = "usr_2", nickname = "러너B", avatarId = "avatar_05",
                        runTogetherCount = 3, lastRunTogetherAt = "2026-09-20T21:30:00Z",
                    ),
                ),
            ),
            onBackClick = {},
            onRetryClick = {},
            onRemoveClick = {},
            onRemoveDialogDismiss = {},
            onRemoveConfirm = {},
            onErrorMessageShown = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun RunningMateListScreenEmptyPreview() {
    DallimTheme {
        RunningMateListScreen(
            uiState = RunningMateListUiState.Success(items = emptyList()),
            onBackClick = {},
            onRetryClick = {},
            onRemoveClick = {},
            onRemoveDialogDismiss = {},
            onRemoveConfirm = {},
            onErrorMessageShown = {},
        )
    }
}
