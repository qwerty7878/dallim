package com.dallim.app.dallimbook.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.dallimbook.DallimbookRunItem
import com.dallim.network.user.SavedRouteItem
import com.dallim.ui.components.DallimBottomNavigation
import com.dallim.ui.components.DallimEmptyState
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimTab
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.RouteThumbnailView
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * S-40 달림북 그리드 (docs/01-feature-spec.md §1.4) — 완주 GPS 그림 2열 그리드 + 빈 슬롯
 * ("저장했지만 미완주 코스"). 각 칸의 GPS 그림은 [RouteThumbnailView]로만 렌더링한다
 * (docs/03-design-system.md §3.2 — 제네릭 아이콘·클립아트 절대 금지).
 * 하단 탭바 도입(§1.0) 후에도 결과 화면(S-25)에서 push로 진입할 수 있어 상단 뒤로가기 버튼은
 * 유지한다 — 탭 클릭으로 들어왔을 때는 눌러도 홈으로 돌아갈 뿐이라 무해하다.
 */
@Composable
fun DallimbookGridRoute(
    onBackClick: () -> Unit,
    onArtworkClick: (runId: String) -> Unit,
    onEmptySlotClick: (routeId: String) -> Unit,
    onTabSelected: (DallimTab) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DallimbookGridViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DallimbookGridScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onArtworkClick = onArtworkClick,
        onEmptySlotClick = onEmptySlotClick,
        onTabSelected = onTabSelected,
        onRetryClick = viewModel::retry,
        onLoadNextPage = viewModel::loadNextPage,
        modifier = modifier,
    )
}

@Composable
private fun DallimbookGridScreen(
    uiState: DallimbookGridUiState,
    onBackClick: () -> Unit,
    onArtworkClick: (String) -> Unit,
    onEmptySlotClick: (String) -> Unit,
    onTabSelected: (DallimTab) -> Unit,
    onRetryClick: () -> Unit,
    onLoadNextPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadNextPage() }
    }

    Scaffold(
        modifier = modifier,
        containerColor = DallimColors.Background,
        bottomBar = {
            DallimBottomNavigation(selectedTab = DallimTab.DALLIMBOOK, onTabSelected = onTabSelected)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DallimColors.Background)
                .padding(innerPadding),
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
                Text(text = "달림북", style = DallimTypography.Title1, color = DallimColors.TextPrimary)
            }

            when {
                uiState.isLoadingInitial -> DallimLoadingState(modifier = Modifier.weight(1f))
                uiState.errorMessage != null && uiState.slots.isEmpty() -> DallimErrorState(
                    title = "달림북을 불러오지 못했어요",
                    description = uiState.errorMessage,
                    onRetry = onRetryClick,
                    modifier = Modifier.weight(1f),
                )
                uiState.slots.isEmpty() -> DallimEmptyState(
                    title = "아직 달림북이 비어있어요",
                    description = "코스를 저장하고 완주하면 여기에 GPS 그림이 쌓여요.",
                    modifier = Modifier.weight(1f),
                )
                else -> Column(modifier = Modifier.weight(1f)) {
                    SummaryRow(totalCount = uiState.totalCount, totalDistanceKm = uiState.totalDistanceKm)

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = Spacing.ScreenHorizontal,
                            end = Spacing.ScreenHorizontal,
                            top = Spacing.sm,
                            bottom = Spacing.xxl,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        items(
                            items = uiState.slots,
                            key = { slot ->
                                when (slot) {
                                    is DallimbookSlot.Artwork -> slot.run.runId
                                    is DallimbookSlot.Empty -> "empty_${slot.savedRoute.routeId}"
                                }
                            },
                        ) { slot ->
                            when (slot) {
                                is DallimbookSlot.Artwork -> ArtworkCell(
                                    run = slot.run,
                                    onClick = { onArtworkClick(slot.run.runId) },
                                )
                                is DallimbookSlot.Empty -> EmptySlotCell(
                                    savedRoute = slot.savedRoute,
                                    onClick = { onEmptySlotClick(slot.savedRoute.routeId) },
                                )
                            }
                        }
                        if (uiState.isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(modifier = Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = DallimColors.Primary)
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
private fun SummaryRow(totalCount: Int, totalDistanceKm: Double) {
    Text(
        text = "완주 ${totalCount}개 · 총 ${"%.1f".format(totalDistanceKm)}km",
        style = DallimTypography.Caption,
        color = DallimColors.TextSecondary,
        modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.sm),
    )
}

@Composable
private fun ArtworkCell(run: DallimbookRunItem, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        RouteThumbnailView(
            coordinates = run.thumbnailGeoJson.toGeoPoints(),
            useGradient = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = run.routeName,
            style = DallimTypography.Body,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            text = "${run.distanceKm}km · ${run.completedAt.toShortDateLabel()}",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/**
 * 저장했지만 아직 완주하지 않은 코스의 빈 슬롯 — 잠금 아이콘 + 그레이아웃 톤으로 표시한다
 * (docs/01-feature-spec.md §1.4). 실제로 달린 GPS 궤적이 없으니 [RouteThumbnailView]로 그릴
 * 데이터 자체가 없다 — 제네릭 클립아트 대신, 이 칸이 "아직 그려지지 않은 이 코스"라는 정보를
 * 그대로 전달하는 잠금 상태를 보여준다. 탭하면 코스 상세(S-16)로 이동해 바로 달릴 수 있다.
 */
@Composable
private fun EmptySlotCell(savedRoute: SavedRouteItem, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(DallimColors.Border.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = DallimColors.TextSecondary,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = "${savedRoute.emoji} ${savedRoute.name}",
            style = DallimTypography.Body,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            text = "아직 완주 전",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

private fun GeoJsonLineString.toGeoPoints(): List<GeoPoint> =
    toLngLatPairs().map { (lng, lat) -> GeoPoint(lng = lng, lat = lat) }

private fun String.toShortDateLabel(): String = runCatching {
    OffsetDateTime.parse(this).format(DateTimeFormatter.ofPattern("MM/dd"))
}.getOrDefault(take(10))

private const val LOAD_MORE_THRESHOLD = 4

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun DallimbookGridScreenPreview() {
    DallimTheme {
        DallimbookGridScreen(
            uiState = DallimbookGridUiState(
                emptySlots = listOf(
                    DallimbookSlot.Empty(
                        SavedRouteItem(routeId = "rt_002", name = "물고기", emoji = "🐟", distanceKm = 4.2, hasRun = false),
                    ),
                ),
                artworkSlots = listOf(
                    DallimbookSlot.Artwork(
                        DallimbookRunItem(
                            runId = "run_301",
                            routeName = "고래",
                            distanceKm = 5.18,
                            completedAt = "2026-08-23T09:34:38Z",
                            thumbnailGeoJson = GeoJsonLineString(
                                coordinates = listOf(
                                    listOf(127.05, 37.25),
                                    listOf(127.052, 37.253),
                                    listOf(127.055, 37.251),
                                    listOf(127.058, 37.256),
                                ),
                            ),
                        ),
                    ),
                ),
                totalCount = 1,
                totalDistanceKm = 5.18,
                isLoadingInitial = false,
            ),
            onBackClick = {},
            onArtworkClick = {},
            onEmptySlotClick = {},
            onTabSelected = {},
            onRetryClick = {},
            onLoadNextPage = {},
        )
    }
}
