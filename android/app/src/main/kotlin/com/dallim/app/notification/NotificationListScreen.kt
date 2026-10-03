package com.dallim.app.notification

import com.dallim.ui.icons.DallimIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.network.notification.NotificationItem
import com.dallim.ui.components.DallimEmptyState
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimTopBar
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * S-46 알림 목록 — 내 알림 최신순 조회, 항목 탭 시 읽음 처리 (docs/01-feature-spec.md §1.7,
 * docs/02-api-spec.md 9장). 이번 라운드는 인앱 알림함만 다룬다 — 관련 러닝(`relatedRunId`)으로
 * 이동하는 것은 SPEC에 없어 하지 않는다(임의 확장 금지, CLAUDE.md 공통 규칙 1). 읽음 처리는
 * `POST /notifications/{id}/read`가 멱등이라 이미 읽은 항목을 다시 눌러도 그대로 재호출한다.
 */
@Composable
fun NotificationListRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NotificationListScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onNotificationClick = viewModel::onNotificationClick,
        onRetryClick = viewModel::retry,
        onLoadNextPage = viewModel::loadNextPage,
        modifier = modifier,
    )
}

@Composable
private fun NotificationListScreen(
    uiState: NotificationListUiState,
    onBackClick: () -> Unit,
    onNotificationClick: (String) -> Unit,
    onRetryClick: () -> Unit,
    onLoadNextPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadNextPage() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        DallimTopBar(title = "알림", onBackClick = onBackClick)

        when {
            uiState.isLoadingInitial -> DallimLoadingState(modifier = Modifier.weight(1f))
            uiState.errorMessage != null && uiState.items.isEmpty() -> DallimErrorState(
                title = "알림을 불러오지 못했어요",
                description = uiState.errorMessage,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            uiState.items.isEmpty() -> DallimEmptyState(
                title = "아직 알림이 없어요",
                description = "코스를 완주하면 여기에서 알려드릴게요.",
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Spacing.ScreenHorizontal,
                    end = Spacing.ScreenHorizontal,
                    top = Spacing.sm,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.ListItemGap),
            ) {
                items(uiState.items, key = { it.id }) { notification ->
                    NotificationRow(
                        notification = notification,
                        onClick = { onNotificationClick(notification.id) },
                    )
                }
                if (uiState.isLoadingMore) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DallimColors.Primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: NotificationItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DallimColors.Surface)
            .clickable { onClick() }
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DallimColors.PrimaryLight),
            contentAlignment = Alignment.Center,
        ) {
            // 이번 라운드는 RUN_COMPLETED 트리거만 오므로(docs/02-api-spec.md 9.4) 완주 트로피
            // 아이콘 하나로 충분하다 — 벡터 아이콘, 이모지 아님(docs/04-ui-guide.md §7).
            Icon(imageVector = DallimIcons.Trophy, contentDescription = null, tint = DallimColors.Primary)
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
            Text(
                text = notification.title,
                style = DallimTypography.Body,
                fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold,
                color = DallimColors.TextPrimary,
            )
            Text(
                text = notification.body,
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Text(
                text = notification.createdAt.toDateTimeLabel(),
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        if (!notification.isRead) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(DallimColors.Error),
            )
        }
    }
}

private fun String.toDateTimeLabel(): String = runCatching {
    OffsetDateTime.parse(this).format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
}.getOrDefault(take(10))

private const val LOAD_MORE_THRESHOLD = 4

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun NotificationListScreenPreview() {
    DallimTheme {
        NotificationListScreen(
            uiState = NotificationListUiState(
                items = listOf(
                    NotificationItem(
                        id = "ntf_8f2a",
                        type = "RUN_COMPLETED",
                        title = "완주를 축하드려요! 🎉",
                        body = "고래 코스 5.10km를 완주했어요.",
                        relatedRunId = "run_1a2b",
                        isRead = false,
                        createdAt = "2026-09-03T05:12:00Z",
                    ),
                    NotificationItem(
                        id = "ntf_7c1b",
                        type = "RUN_COMPLETED",
                        title = "완주를 축하드려요! 🎉",
                        body = "물고기 코스 3.40km를 완주했어요.",
                        relatedRunId = "run_1a1a",
                        isRead = true,
                        createdAt = "2026-09-01T09:02:00Z",
                    ),
                ),
                isLoadingInitial = false,
            ),
            onBackClick = {},
            onNotificationClick = {},
            onRetryClick = {},
            onLoadNextPage = {},
        )
    }
}
