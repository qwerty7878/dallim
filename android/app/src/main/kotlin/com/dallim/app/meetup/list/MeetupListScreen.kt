package com.dallim.app.meetup.list

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.network.meetup.MeetupListItem
import com.dallim.ui.components.DallimEmptyState
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimTopBar
import com.dallim.ui.components.MeetupStatusBadge
import com.dallim.ui.components.meetupBadgeState
import com.dallim.ui.components.DallimFab
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing
import com.dallim.app.meetup.MeetupFormat

/**
 * S-47 모집 목록 — 특정 코스의 모집 게시글 최신순(정확히는 `scheduledAt` 오름차순, 서버가 이미
 * 정렬해서 내려준다) 목록과 [모집 만들기] 진입점 (docs/01-feature-spec.md §1.8,
 * docs/02-api-spec.md 14.2). [모집 만들기] 버튼은 "S-47에는 항상 [모집 만들기] 버튼이 보인다"
 * (§1.8.1)는 규칙에 따라 로딩/에러/빈 상태와 무관하게 항상 떠 있어야 하므로, 이 화면만은
 * 예외적으로(§6 규칙은 하단 탭바가 있는 4개 최상위 화면에만 `Scaffold`를 허용하지만, 여기서는
 * `Scaffold` 없이) `Box` 오버레이로 FAB를 본문 상태와 무관하게 고정 배치한다.
 *
 * S-48(모집 만들기)/S-49(모집 상세) 화면 자체는 이번 라운드에서 구현하지 않는다 — [onCreateClick]/
 * [onMeetupClick] 콜백만 노출해 NavHost에서 해당 라우트로 이동을 연결해둔다.
 */
@Composable
fun MeetupListRoute(
    onBackClick: () -> Unit,
    onMeetupClick: (meetupId: String) -> Unit,
    onCreateClick: (routeId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeetupListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MeetupListScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onMeetupClick = onMeetupClick,
        // S-48(모집 만들기)은 이 화면과 같은 routeId를 대상으로 열려야 하므로, ViewModel이
        // savedStateHandle에서 이미 읽어둔 routeId를 그대로 넘긴다.
        onCreateClick = { onCreateClick(viewModel.routeId) },
        onRetryClick = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun MeetupListScreen(
    uiState: MeetupListUiState,
    onBackClick: () -> Unit,
    onMeetupClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(DallimColors.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            DallimTopBar(title = "같이 달리기 모집", onBackClick = onBackClick)

            when (uiState) {
                MeetupListUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
                is MeetupListUiState.Error -> DallimErrorState(
                    title = "모집 목록을 불러오지 못했어요",
                    description = uiState.message,
                    onRetry = onRetryClick,
                    modifier = Modifier.weight(1f),
                )
                is MeetupListUiState.Success -> {
                    if (uiState.items.isEmpty()) {
                        DallimEmptyState(
                            title = "아직 등록된 모집이 없어요",
                            description = "[모집 만들기]로 이 코스에서 같이 달릴 사람을 모아보세요.",
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                start = Spacing.ScreenHorizontal,
                                end = Spacing.ScreenHorizontal,
                                top = Spacing.sm,
                                bottom = Spacing.xxl + FabClearance,
                            ),
                        ) {
                            // 2026-10-05: 같은 회색 카드가 끝없이 반복되면 "자동 생성된 목록"처럼 보인다
                            // (docs/04-ui-guide.md §0 #4, §4). 카드 껍데기를 벗기고 구분선 목록으로 폈다.
                            itemsIndexed(uiState.items, key = { _, m -> m.meetupId }) { index, meetup ->
                                if (index > 0) HorizontalDivider(color = DallimColors.Divider)
                                MeetupRow(meetup = meetup, onClick = { onMeetupClick(meetup.meetupId) })
                            }
                        }
                    }
                }
            }
        }

        // §1.8.1: [모집 만들기] 버튼은 로딩/에러/빈 상태와 무관하게 항상 보인다.
        DallimFab(text = "모집 만들기", icon = DallimIcons.Plus, onClick = onCreateClick, modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(Spacing.ScreenHorizontal),)
    }
}

@Composable
private fun MeetupRow(meetup: MeetupListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 끝난 모집은 목록 뒤로 밀릴 뿐 아니라 시각적으로도 가라앉혀, 지금 참가할 수 있는 모집이 먼저
    // 눈에 들어오게 한다(2026-10-05 — 서버 정렬은 com.dallim.meetup.MeetupService.listByRoute).
    val ended = meetup.isPast || meetup.status == "CANCELLED"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .alpha(if (ended) 0.5f else 1f)
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DallimColors.PrimaryLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = DallimIcons.User,
                contentDescription = null,
                tint = DallimColors.Primary,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
            // 이 화면에서 제일 먼저 읽어야 하는 건 호스트 이름이 아니라 "언제"다 — 날짜를 제목 자리로 올렸다.
            Text(
                text = MeetupFormat.displayDateTime(meetup.scheduledAt),
                style = DallimTypography.Title3,
                color = DallimColors.TextPrimary,
            )
            Text(
                text = "${meetup.hostNickname} \u00b7 ${meetup.currentParticipants}/${meetup.maxParticipants}명 참가",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        MeetupStatusBadge(state = meetupBadgeState(meetup.status, meetup.isFull, meetup.isPast))
    }
}

/** 리스트 마지막 아이템이 FAB에 가리지 않도록 주는 추가 하단 여백. */
private val FabClearance = 64.dp

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun MeetupListScreenPreview() {
    DallimTheme {
        MeetupListScreen(
            uiState = MeetupListUiState.Success(
                items = listOf(
                    MeetupListItem(
                        meetupId = "mt_001",
                        hostNickname = "달림이",
                        scheduledAt = "2026-09-13T22:00:00Z",
                        maxParticipants = 6,
                        currentParticipants = 3,
                        status = "OPEN",
                        isFull = false,
                        isPast = false,
                    ),
                    MeetupListItem(
                        meetupId = "mt_002",
                        hostNickname = "러너B",
                        scheduledAt = "2026-09-14T11:00:00Z",
                        maxParticipants = 4,
                        currentParticipants = 4,
                        status = "OPEN",
                        isFull = true,
                        isPast = false,
                    ),
                    MeetupListItem(
                        meetupId = "mt_003",
                        hostNickname = "숲속러너",
                        scheduledAt = "2026-09-01T09:00:00Z",
                        maxParticipants = 5,
                        currentParticipants = 2,
                        status = "OPEN",
                        isFull = false,
                        isPast = true,
                    ),
                    MeetupListItem(
                        meetupId = "mt_004",
                        hostNickname = "새벽러너",
                        scheduledAt = "2026-09-20T10:00:00Z",
                        maxParticipants = 8,
                        currentParticipants = 1,
                        status = "CANCELLED",
                        isFull = false,
                        isPast = false,
                    ),
                ),
            ),
            onBackClick = {},
            onMeetupClick = {},
            onCreateClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun MeetupListScreenEmptyPreview() {
    DallimTheme {
        MeetupListScreen(
            uiState = MeetupListUiState.Success(items = emptyList()),
            onBackClick = {},
            onMeetupClick = {},
            onCreateClick = {},
            onRetryClick = {},
        )
    }
}
