package com.dallim.app.social.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.social.SocialSessionFormat
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.social.SocialSessionInboxItem
import com.dallim.network.social.SocialSessionInboxLastMessage
import com.dallim.ui.components.DallimBottomNavigation
import com.dallim.ui.components.DallimEmptyState
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimTab
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.RouteThumbnailView
import com.dallim.ui.components.SocialSessionStatusBadge
import com.dallim.ui.components.socialSessionBadgeState
import com.dallim.ui.components.DallimBadge
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * 채팅 탭 (2026-09-16 신규, v1.3 SPEC 밖 — docs/02-api-spec.md 18.1). 홈(S-10)/마이(S-42)와
 * 동일한 최상위 탭 화면 패턴(뒤로가기 버튼 없음, 하단 탭바로만 진입/이탈). 각 행은 세션 제목,
 * 마지막 메시지 미리보기(없으면 예정 시각), 호스트 뱃지, 세션 상태 뱃지를 보여준다. 읽음/안읽음
 * 표시는 만들지 않는다(18.3 — 서버에 그 데이터 자체가 없음, 과설계 금지).
 */
@Composable
fun ChatInboxRoute(
    onTabSelected: (DallimTab) -> Unit,
    onSessionClick: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatInboxViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ChatInboxScreen(
        uiState = uiState,
        onTabSelected = onTabSelected,
        onSessionClick = onSessionClick,
        onRetryClick = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun ChatInboxScreen(
    uiState: ChatInboxUiState,
    onTabSelected: (DallimTab) -> Unit,
    onSessionClick: (String) -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = DallimColors.Background,
        bottomBar = {
            DallimBottomNavigation(selectedTab = DallimTab.CHAT, onTabSelected = onTabSelected)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DallimColors.Background)
                .padding(innerPadding),
        ) {
            Text(
                text = "채팅",
                style = DallimTypography.Title1,
                color = DallimColors.TextPrimary,
                modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md),
            )

            when (uiState) {
                ChatInboxUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
                is ChatInboxUiState.Error -> DallimErrorState(
                    title = "채팅 목록을 불러오지 못했어요",
                    description = uiState.message,
                    onRetry = onRetryClick,
                    modifier = Modifier.weight(1f),
                )
                is ChatInboxUiState.Success -> {
                    if (uiState.items.isEmpty()) {
                        DallimEmptyState(
                            title = "아직 참가 중인 세션이 없어요",
                            description = "탐색의 [소셜] 세그먼트에서 세션에 참가하거나 직접 열어보세요.",
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                start = Spacing.ScreenHorizontal,
                                end = Spacing.ScreenHorizontal,
                                top = Spacing.sm,
                                bottom = Spacing.xxl,
                            ),
                            verticalArrangement = Arrangement.spacedBy(Spacing.ListItemGap),
                        ) {
                            items(uiState.items, key = { it.sessionId }) { item ->
                                ChatInboxRow(item = item, onClick = { onSessionClick(item.sessionId) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInboxRow(item: SocialSessionInboxItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(DallimShapes.CardCorner)
            .background(DallimColors.Surface)
            .clickable { onClick() }
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouteThumbnailView(
            coordinates = item.routeThumbnailGeoJson?.toGeoPoints() ?: emptyList(),
            modifier = Modifier.size(56.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = DallimTypography.Body,
                    color = DallimColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (item.isHost) {
                    HostBadge(modifier = Modifier.padding(start = Spacing.xs))
                }
                SocialSessionStatusBadge(
                    state = socialSessionBadgeState(item.status),
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
            Text(
                text = item.lastMessage.toPreviewText(item.scheduledAt),
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Text(
                text = "승인됨 ${item.approvedCount}/${item.maxParticipants}명",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun HostBadge(modifier: Modifier = Modifier) {
    DallimBadge(label = "호스트", foreground = DallimColors.Primary, modifier = modifier)
}

/** 채팅이 한 번도 없었으면 예정 시각을, 시스템 메시지면 발신자 없이 본문만, 그 외엔
 * "닉네임: 본문"을 보여준다. */
private fun SocialSessionInboxLastMessage?.toPreviewText(scheduledAt: String): String {
    if (this == null) return "아직 대화가 없어요 · ${SocialSessionFormat.displayDateTime(scheduledAt)}"
    return if (senderNickname != null) "$senderNickname: $body" else body
}

private fun GeoJsonLineString.toGeoPoints(): List<GeoPoint> =
    toLngLatPairs().map { (lng, lat) -> GeoPoint(lng = lng, lat = lat) }

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ChatInboxScreenPreview() {
    DallimTheme {
        ChatInboxScreen(
            uiState = ChatInboxUiState.Success(
                items = listOf(
                    SocialSessionInboxItem(
                        sessionId = "ss_001",
                        title = "안양천 야간 러닝",
                        routeThumbnailGeoJson = GeoJsonLineString(
                            coordinates = listOf(listOf(126.9, 37.5), listOf(126.91, 37.51)),
                        ),
                        scheduledAt = "2026-09-20T21:00:00Z",
                        isHost = true,
                        approvedCount = 3,
                        maxParticipants = 6,
                        status = "NEAR_CONFIRMATION",
                        lastMessage = SocialSessionInboxLastMessage(
                            body = "오늘 20분 늦게 시작합니다!",
                            type = "HOST_ANNOUNCEMENT",
                            createdAt = "2026-09-15T14:36:56Z",
                            senderNickname = "달림이",
                        ),
                    ),
                    SocialSessionInboxItem(
                        sessionId = "ss_002",
                        title = "판교 러닝 크루 정모",
                        routeThumbnailGeoJson = null,
                        scheduledAt = "2026-09-25T10:00:00Z",
                        isHost = false,
                        approvedCount = 6,
                        maxParticipants = 8,
                        status = "CONFIRMED",
                        lastMessage = null,
                    ),
                ),
            ),
            onTabSelected = {},
            onSessionClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ChatInboxScreenEmptyPreview() {
    DallimTheme {
        ChatInboxScreen(
            uiState = ChatInboxUiState.Success(items = emptyList()),
            onTabSelected = {},
            onSessionClick = {},
            onRetryClick = {},
        )
    }
}
