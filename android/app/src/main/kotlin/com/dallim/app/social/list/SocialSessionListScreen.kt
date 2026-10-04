package com.dallim.app.social.list

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dallim.app.social.SocialAvatarBadge
import com.dallim.app.social.SocialSessionFormat
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.social.SocialSessionListItem
import com.dallim.ui.components.DallimEmptyState
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimFilterChip
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.RouteThumbnailView
import com.dallim.ui.components.SocialSessionStatusBadge
import com.dallim.ui.components.socialSessionBadgeState
import com.dallim.ui.components.DallimFab
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-30 세션 탐색 — 소셜 세션(호스트 승인 워크플로우가 있는 무거운 모집, S-30~S-39) 목록
 * (docs/달림_화면별_상세기획서_v1.3.md PART 3-D, docs/02-api-spec.md 17.2). 지도 핀/리스트
 * 토글과 세밀한 필터는 이번 1단계 범위 밖 — 리스트만 구현한다(과설계 금지, 작업 브리핑 참고).
 * 2026-09-16부터 탐색(S-11, [com.dallim.app.discover.DiscoverScreen])의
 * `[그림 코스]/[소셜]` 세그먼트 중 `[소셜]` 안에서 인라인으로 렌더링된다 — 이 컴포저블은
 * 헤더나 세그먼트 칩을 그리지 않고 목록+FAB만 담당한다(2026-09-15에 잠깐 있었던 소셜 허브
 * 통합 탭은 폐기됨).
 */
@Composable
fun SocialSessionListBody(
    uiState: SocialSessionListUiState,
    onSessionClick: (sessionId: String) -> Unit,
    onCreateClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(DallimColors.Background)) {
        when (uiState) {
            SocialSessionListUiState.Loading -> DallimLoadingState(modifier = Modifier.fillMaxSize())
            is SocialSessionListUiState.Error -> DallimErrorState(
                title = "세션 목록을 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryClick,
                modifier = Modifier.fillMaxSize(),
            )
            is SocialSessionListUiState.Success -> {
                if (uiState.items.isEmpty()) {
                    DallimEmptyState(
                        title = "이 지역엔 아직 모집 중인 달림이 없어요",
                        description = "내가 첫 세션을 열어 같이 달릴 사람을 모아보세요.",
                        actionText = "내가 첫 세션 열기",
                        onActionClick = onCreateClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.ScreenHorizontal,
                            end = Spacing.ScreenHorizontal,
                            top = Spacing.sm,
                            bottom = Spacing.xxl + FabClearance,
                        ),
                    ) {
                        // 2026-10-05: 같은 회색 카드가 끝없이 반복되면 "자동 생성된 목록"처럼 보인다
                        // (docs/04-ui-guide.md §0 #10, §4). 카드 껍데기를 벗기고 구분선 목록으로 폈다.
                        itemsIndexed(uiState.items, key = { _, it -> it.sessionId }) { index, session ->
                            if (index > 0) HorizontalDivider(color = DallimColors.Divider)
                            SocialSessionRow(session = session, onClick = { onSessionClick(session.sessionId) })
                        }
                    }
                }
            }
        }

        DallimFab(text = "세션 열기", icon = DallimIcons.Plus, onClick = onCreateClick, modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(Spacing.ScreenHorizontal),)
    }
}

@Composable
private fun SocialSessionRow(session: SocialSessionListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 끝난 세션은 서버 정렬로 목록 뒤로 밀릴 뿐 아니라(docs/02-api-spec.md 17.2) 시각적으로도 가라앉혀,
    // 지금 신청할 수 있는 세션이 먼저 눈에 들어오게 한다(2026-10-05).
    val ended = session.status == "CLOSED" || session.status == "CANCELLED"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .alpha(if (ended) 0.5f else 1f)
            .padding(vertical = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            RouteThumbnailView(
                coordinates = session.routeThumbnailGeoJson?.toGeoPoints() ?: emptyList(),
                modifier = Modifier.size(64.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = session.title,
                        style = DallimTypography.Body,
                        color = DallimColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    SocialSessionStatusBadge(state = socialSessionBadgeState(session.status))
                }
                Text(
                    text = SocialSessionFormat.displayDateTime(session.scheduledAt),
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Text(
                    text = "승인됨 ${session.approvedCount}/${session.maxParticipants}명",
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SocialAvatarBadge(avatarId = session.hostAvatarId, size = 24.dp)
            Text(
                text = "${session.hostNickname} · ${SocialSessionFormat.displayTemperature(session.hostRunningTemperature)}",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }

        if (session.beginnerFriendly || session.runningStyles.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (session.beginnerFriendly) {
                    item { DallimFilterChip(label = "초보환영", selected = false, onClick = {}) }
                }
                items(session.runningStyles) { style ->
                    DallimFilterChip(label = style, selected = false, onClick = {})
                }
            }
        }
    }
}

private fun GeoJsonLineString.toGeoPoints(): List<GeoPoint> =
    toLngLatPairs().map { (lng, lat) -> GeoPoint(lng = lng, lat = lat) }

private val FabClearance = 64.dp

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SocialSessionListBodyPreview() {
    DallimTheme {
        SocialSessionListBody(
            uiState = SocialSessionListUiState.Success(
                items = listOf(
                    SocialSessionListItem(
                        sessionId = "ss_001",
                        title = "안양천 야간 러닝",
                        scheduledAt = "2026-09-20T21:00:00Z",
                        routeId = "rt_004",
                        routeThumbnailGeoJson = GeoJsonLineString(
                            coordinates = listOf(listOf(126.9, 37.5), listOf(126.91, 37.51), listOf(126.92, 37.505)),
                        ),
                        approvedCount = 3,
                        minParticipants = 4,
                        maxParticipants = 6,
                        runningStyles = listOf("대화하면서", "초보환영조합"),
                        hostUserId = "usr_1",
                        hostNickname = "달림이",
                        hostAvatarId = "avatar_02",
                        hostRunningTemperature = 37.2,
                        beginnerFriendly = true,
                        status = "NEAR_CONFIRMATION",
                    ),
                    SocialSessionListItem(
                        sessionId = "ss_002",
                        title = "판교 러닝 크루 정모",
                        scheduledAt = "2026-09-25T10:00:00Z",
                        routeId = "rt_010",
                        routeThumbnailGeoJson = null,
                        approvedCount = 6,
                        minParticipants = 6,
                        maxParticipants = 8,
                        runningStyles = emptyList(),
                        hostUserId = "usr_2",
                        hostNickname = "러너B",
                        hostAvatarId = null,
                        hostRunningTemperature = 36.5,
                        beginnerFriendly = false,
                        status = "CONFIRMED",
                    ),
                ),
            ),
            onSessionClick = {},
            onCreateClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SocialSessionListBodyEmptyPreview() {
    DallimTheme {
        SocialSessionListBody(
            uiState = SocialSessionListUiState.Success(items = emptyList()),
            onSessionClick = {},
            onCreateClick = {},
            onRetryClick = {},
        )
    }
}
