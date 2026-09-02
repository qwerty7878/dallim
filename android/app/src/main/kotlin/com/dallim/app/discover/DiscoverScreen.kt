package com.dallim.app.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.running.RunFormat
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.route.RouteListItem
import com.dallim.ui.components.DallimBottomNavigation
import com.dallim.ui.components.DallimEmptyState
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimFilterChip
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimTab
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.RouteStatusBadge
import com.dallim.ui.components.RouteThumbnailView
import com.dallim.ui.components.toRouteStatus
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * S-11 탐색(코스 리스트) — 필터 + 무한스크롤 (docs/01-feature-spec.md §1.2).
 * 하단 탭바 도입(§1.0) 후에도 다른 화면(저장한 코스 등)에서 push로 진입할 수 있어
 * 상단 뒤로가기 버튼은 유지한다 — 탭 클릭으로 들어왔을 때는 눌러도 홈으로 돌아갈 뿐이라 무해하다.
 */
@Composable
fun DiscoverRoute(
    onBackClick: () -> Unit,
    onRouteClick: (routeId: String) -> Unit,
    onTabSelected: (DallimTab) -> Unit,
    onCreateCourseClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DiscoverScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRouteClick = onRouteClick,
        onTabSelected = onTabSelected,
        onCreateCourseClick = onCreateCourseClick,
        onDistanceFilterChange = viewModel::onDistanceFilterChange,
        onStatusFilterChange = viewModel::onStatusFilterChange,
        onSortChange = viewModel::onSortChange,
        onRetryClick = viewModel::retry,
        onLoadNextPage = viewModel::loadNextPage,
        onToggleSaveClick = viewModel::onToggleSave,
        modifier = modifier,
    )
}

@Composable
private fun DiscoverScreen(
    uiState: DiscoverUiState,
    onBackClick: () -> Unit,
    onRouteClick: (String) -> Unit,
    onTabSelected: (DallimTab) -> Unit,
    onCreateCourseClick: () -> Unit,
    onDistanceFilterChange: (DistanceFilter) -> Unit,
    onStatusFilterChange: (RouteStatusFilter) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onRetryClick: () -> Unit,
    onLoadNextPage: () -> Unit,
    onToggleSaveClick: (String) -> Unit,
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

    Scaffold(
        modifier = modifier,
        containerColor = DallimColors.Background,
        bottomBar = {
            DallimBottomNavigation(selectedTab = DallimTab.EXPLORE, onTabSelected = onTabSelected)
        },
        floatingActionButton = {
            // S-43 코스 만들기 진입점 (오케스트레이터 지시로 신설 — docs/01-feature-spec.md에는
            // 없던 화면, docs/02-api-spec.md 8장 API에 맞춰 조기 구현).
            ExtendedFloatingActionButton(
                onClick = onCreateCourseClick,
                containerColor = DallimColors.Primary,
                contentColor = DallimColors.Surface,
                icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                text = { Text(text = "코스 만들기") },
            )
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
                Text(text = "탐색", style = DallimTypography.Title1, color = DallimColors.TextPrimary)
            }

            FilterSection(
                distanceFilter = uiState.distanceFilter,
                statusFilter = uiState.statusFilter,
                sort = uiState.sort,
                onDistanceFilterChange = onDistanceFilterChange,
                onStatusFilterChange = onStatusFilterChange,
                onSortChange = onSortChange,
            )

            when {
                uiState.isLoadingInitial -> DallimLoadingState(modifier = Modifier.weight(1f))
                uiState.errorMessage != null && uiState.items.isEmpty() -> DallimErrorState(
                    title = "코스를 불러오지 못했어요",
                    description = uiState.errorMessage,
                    onRetry = onRetryClick,
                    modifier = Modifier.weight(1f),
                )
                uiState.items.isEmpty() -> DallimEmptyState(
                    title = "조건에 맞는 코스가 없어요",
                    description = "필터를 바꿔서 다시 찾아보세요.",
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
                    items(uiState.items, key = { it.routeId }) { route ->
                        RouteRow(
                            route = route,
                            isTogglingSave = route.routeId in uiState.togglingSaveRouteIds,
                            onClick = { onRouteClick(route.routeId) },
                            onToggleSaveClick = { onToggleSaveClick(route.routeId) },
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
}

@Composable
private fun FilterSection(
    distanceFilter: DistanceFilter,
    statusFilter: RouteStatusFilter,
    sort: SortOption,
    onDistanceFilterChange: (DistanceFilter) -> Unit,
    onStatusFilterChange: (RouteStatusFilter) -> Unit,
    onSortChange: (SortOption) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = Spacing.sm)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.ScreenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(DistanceFilter.entries.toList()) { option ->
                DallimFilterChip(
                    label = option.label,
                    selected = distanceFilter == option,
                    onClick = { onDistanceFilterChange(option) },
                )
            }
        }
        LazyRow(
            modifier = Modifier.padding(top = Spacing.sm),
            contentPadding = PaddingValues(horizontal = Spacing.ScreenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(RouteStatusFilter.entries.toList()) { option ->
                DallimFilterChip(
                    label = option.label,
                    selected = statusFilter == option,
                    onClick = { onStatusFilterChange(option) },
                )
            }
        }
        LazyRow(
            modifier = Modifier.padding(top = Spacing.sm),
            contentPadding = PaddingValues(horizontal = Spacing.ScreenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(SortOption.entries.toList()) { option ->
                DallimFilterChip(
                    label = option.label,
                    selected = sort == option,
                    onClick = { onSortChange(option) },
                )
            }
        }
    }
}

@Composable
private fun RouteRow(
    route: RouteListItem,
    isTogglingSave: Boolean,
    onClick: () -> Unit,
    onToggleSaveClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DallimColors.Surface)
            .clickable { onClick() }
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouteThumbnailView(
            coordinates = route.thumbnailGeoJson.toGeoPoints(),
            modifier = Modifier.size(64.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = route.emoji, style = DallimTypography.Body)
                Text(
                    text = route.name,
                    style = DallimTypography.Body,
                    color = DallimColors.TextPrimary,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
            Text(
                text = "${RunFormat.km(route.distanceKm)}km · 약 ${route.estimatedMinutes}분",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            RouteStatusBadge(status = route.status.toRouteStatus(), modifier = Modifier.padding(top = Spacing.xs))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${route.finisherCount}명 완주",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
            )
            IconButton(onClick = onToggleSaveClick, enabled = !isTogglingSave) {
                Icon(
                    imageVector = if (route.isSaved) Icons.Filled.Bookmark else Icons.Outlined.Bookmark,
                    contentDescription = if (route.isSaved) "저장 취소" else "코스 저장",
                    tint = if (route.isSaved) DallimColors.TextPrimary else DallimColors.TextSecondary,
                )
            }
        }
    }
}

private fun GeoJsonLineString.toGeoPoints(): List<GeoPoint> =
    toLngLatPairs().map { (lng, lat) -> GeoPoint(lng = lng, lat = lat) }

private const val LOAD_MORE_THRESHOLD = 4

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun DiscoverScreenPreview() {
    DallimTheme {
        DiscoverScreen(
            uiState = DiscoverUiState(
                items = listOf(
                    RouteListItem(
                        routeId = "rt_001",
                        name = "고래",
                        emoji = "🐳",
                        distanceKm = 5.1,
                        estimatedMinutes = 36,
                        status = "POPULAR",
                        finisherCount = 148,
                        thumbnailGeoJson = GeoJsonLineString(
                            coordinates = listOf(
                                listOf(127.05, 37.25),
                                listOf(127.052, 37.253),
                                listOf(127.055, 37.251),
                                listOf(127.058, 37.256),
                            ),
                        ),
                        isSaved = true,
                    ),
                    RouteListItem(
                        routeId = "rt_002",
                        name = "물고기",
                        emoji = "🐟",
                        distanceKm = 3.4,
                        estimatedMinutes = 24,
                        status = "DISCOVERY",
                        finisherCount = 12,
                        thumbnailGeoJson = GeoJsonLineString(
                            coordinates = listOf(
                                listOf(127.05, 37.25),
                                listOf(127.053, 37.254),
                                listOf(127.056, 37.252),
                            ),
                        ),
                        isSaved = false,
                    ),
                ),
                isLoadingInitial = false,
            ),
            onBackClick = {},
            onRouteClick = {},
            onTabSelected = {},
            onCreateCourseClick = {},
            onDistanceFilterChange = {},
            onStatusFilterChange = {},
            onSortChange = {},
            onRetryClick = {},
            onLoadNextPage = {},
            onToggleSaveClick = {},
        )
    }
}
