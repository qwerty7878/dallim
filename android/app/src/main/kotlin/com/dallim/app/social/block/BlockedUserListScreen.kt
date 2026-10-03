package com.dallim.app.social.block

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.social.SocialAvatarBadge
import com.dallim.app.social.SocialSessionFormat
import com.dallim.network.user.BlockedUserItem
import com.dallim.ui.components.DallimEmptyState
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimTopBar
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * 차단 관리 (2026-09-16 신규, docs/02-api-spec.md 18.2 — v1.3 SPEC 밖). 마이(S-42)의 진입
 * 카드에서 push로 들어온다. 채팅 메시지 발신자 차단 스코프 한정이라, 여기서 해제해도 세션
 * 신청/매칭 등 다른 곳에는 영향이 없다(18.3, 이번 라운드 범위 밖).
 */
@Composable
fun BlockedUserListRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlockedUserListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BlockedUserListScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetryClick = viewModel::load,
        onUnblockClick = viewModel::onUnblockClick,
        modifier = modifier,
    )
}

@Composable
private fun BlockedUserListScreen(
    uiState: BlockedUserListUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onUnblockClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, containerColor = DallimColors.Background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DallimColors.Background)
                .padding(innerPadding),
        ) {
            DallimTopBar(title = "차단 관리", onBackClick = onBackClick)

            when (uiState) {
                BlockedUserListUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
                is BlockedUserListUiState.Error -> DallimErrorState(
                    title = "차단 목록을 불러오지 못했어요",
                    description = uiState.message,
                    onRetry = onRetryClick,
                    modifier = Modifier.weight(1f),
                )
                is BlockedUserListUiState.Success -> {
                    if (uiState.items.isEmpty()) {
                        DallimEmptyState(
                            title = "차단한 사용자가 없어요",
                            description = "채팅에서 사용자를 차단하면 여기에 표시돼요.",
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            if (uiState.errorMessage != null) {
                                Text(
                                    text = uiState.errorMessage,
                                    style = DallimTypography.Caption,
                                    color = DallimColors.Error,
                                    modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.xs),
                                )
                            }
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    start = Spacing.ScreenHorizontal,
                                    end = Spacing.ScreenHorizontal,
                                    top = Spacing.sm,
                                    bottom = Spacing.xxl,
                                ),
                                verticalArrangement = Arrangement.spacedBy(Spacing.ListItemGap),
                            ) {
                                items(uiState.items, key = { it.userId }) { item ->
                                    BlockedUserRow(
                                        item = item,
                                        isUnblocking = item.userId in uiState.unblockingUserIds,
                                        onUnblockClick = { onUnblockClick(item.userId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedUserRow(
    item: BlockedUserItem,
    isUnblocking: Boolean,
    onUnblockClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DallimColors.Surface, DallimShapes.CardCorner)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SocialAvatarBadge(avatarId = item.avatarId, size = 40.dp)
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm)) {
            Text(text = item.nickname, style = DallimTypography.Body, color = DallimColors.TextPrimary)
            Text(
                text = "${SocialSessionFormat.displayDateTime(item.blockedAt)} 차단함",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
            )
        }
        DallimTextButton(
            text = if (isUnblocking) "해제 중..." else "차단 해제",
            onClick = onUnblockClick,
            enabled = !isUnblocking,
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun BlockedUserListScreenPreview() {
    DallimTheme {
        BlockedUserListScreen(
            uiState = BlockedUserListUiState.Success(
                items = listOf(
                    BlockedUserItem(
                        userId = "usr_hkfob938",
                        nickname = "달림이",
                        avatarId = null,
                        blockedAt = "2026-09-15T14:37:22Z",
                    ),
                ),
            ),
            onBackClick = {},
            onRetryClick = {},
            onUnblockClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun BlockedUserListScreenEmptyPreview() {
    DallimTheme {
        BlockedUserListScreen(
            uiState = BlockedUserListUiState.Success(items = emptyList()),
            onBackClick = {},
            onRetryClick = {},
            onUnblockClick = {},
        )
    }
}
