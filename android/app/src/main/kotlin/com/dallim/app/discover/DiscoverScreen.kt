package com.dallim.app.discover

import com.dallim.ui.icons.DallimIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.running.RunFormat
import com.dallim.app.social.list.SocialSessionListBody
import com.dallim.app.social.list.SocialSessionListViewModel
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.route.RouteListItem
import com.dallim.ui.components.DallimBottomNavigation
import com.dallim.ui.components.DallimDropdownText
import com.dallim.ui.components.DallimEmptyState
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimFilterChip
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimTab
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.RouteStatusBadge
import com.dallim.ui.components.RouteThumbnailView
import com.dallim.ui.components.toRouteStatus
import com.dallim.ui.components.DallimFab
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * S-11 탐색 — 코스 리스트(필터 + 무한스크롤, docs/01-feature-spec.md §1.2) / 소셜 세션(S-30)
 * `[그림 코스]/[소셜]` 2단 세그먼트. 하단 탭바 도입(§1.0) 후에도 다른 화면(저장한 코스 등)에서
 * push로 진입할 수 있어 상단 뒤로가기 버튼은 유지한다 — 탭 클릭으로 들어왔을 때는 눌러도
 * 홈으로 돌아갈 뿐이라 무해하다.
 *
 * 2026-09-16 재편: 2026-09-15에 대회/소셜 세션을 별도 "소셜" 통합 탭으로 옮겼던 것을 사용자
 * 지시로 되돌렸다. 대회(S-80)는 독립 [DallimTab.RACE] 탭으로 승격했고, 소셜 세션(S-30)은 이
 * 화면의 `[소셜]` 세그먼트로 돌아왔다(대회 칩은 넣지 않는다 — 이제 독립 탭이 있으므로). `[소셜]`
 * 세그먼트를 선택하면 push 이동 없이 그 자리에서 [SocialSessionListBody]를 인라인 렌더링한다 —
 * 이 화면(EXPLORE) 자신의 NavBackStackEntry가 [SocialSessionListViewModel]의
 * `SavedStateHandle` owner가 되어, 세션 생성(S-31) 후 돌아왔을 때 자동 새로고침되는 결과 플래그
 * 릴레이([com.dallim.app.navigation.DallimNavHost]의 `SOCIAL_SESSION_CREATE.onCreated` 참고)가
 * 그대로 동작한다.
 */
@Composable
fun DiscoverRoute(
    onRouteClick: (routeId: String) -> Unit,
    onTabSelected: (DallimTab) -> Unit,
    onCreateCourseClick: () -> Unit,
    onSessionClick: (sessionId: String) -> Unit,
    onCreateSessionClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var segment by rememberSaveable { mutableStateOf(DiscoverSegment.COURSE) }

    DiscoverScreen(
        segment = segment,
        onSegmentChange = { segment = it },
        uiState = uiState,
        onRouteClick = onRouteClick,
        onTabSelected = onTabSelected,
        onCreateCourseClick = onCreateCourseClick,
        onSessionClick = onSessionClick,
        onCreateSessionClick = onCreateSessionClick,
        onDistanceFilterChange = viewModel::onDistanceFilterChange,
        onStatusFilterChange = viewModel::onStatusFilterChange,
        onSortChange = viewModel::onSortChange,
        onRetryClick = viewModel::retry,
        onLoadNextPage = viewModel::loadNextPage,
        onToggleSaveClick = viewModel::onToggleSave,
        modifier = modifier,
    )
}

private enum class DiscoverSegment { COURSE, SOCIAL }

@Composable
private fun DiscoverScreen(
    segment: DiscoverSegment,
    onSegmentChange: (DiscoverSegment) -> Unit,
    uiState: DiscoverUiState,
    onRouteClick: (String) -> Unit,
    onTabSelected: (DallimTab) -> Unit,
    onCreateCourseClick: () -> Unit,
    onSessionClick: (String) -> Unit,
    onCreateSessionClick: () -> Unit,
    onDistanceFilterChange: (DistanceFilter) -> Unit,
    onStatusFilterChange: (RouteStatusFilter) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onRetryClick: () -> Unit,
    onLoadNextPage: () -> Unit,
    onToggleSaveClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyGridState()

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
            // S-43 코스 만들기 진입점(코스 세그먼트 전용) — 소셜 세그먼트는 SocialSessionListBody가
            // 자체 "세션 열기" FAB를 그리므로 여기서는 아무것도 띄우지 않는다.
            if (segment == DiscoverSegment.COURSE) {
                DallimFab(text = "코스 만들기", icon = DallimIcons.Plus, onClick = onCreateCourseClick)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DallimColors.Background)
                .padding(innerPadding),
        ) {
            Text(
                text = "탐색",
                style = DallimTypography.Title1,
                color = DallimColors.TextPrimary,
                modifier = Modifier.padding(
                    start = Spacing.ScreenHorizontal,
                    end = Spacing.ScreenHorizontal,
                    top = Spacing.md,
                    bottom = Spacing.sm,
                ),
            )

            SegmentTabs(
                labels = listOf("그림 코스", "소셜"),
                selectedIndex = if (segment == DiscoverSegment.COURSE) 0 else 1,
                onSelect = { index ->
                    onSegmentChange(if (index == 0) DiscoverSegment.COURSE else DiscoverSegment.SOCIAL)
                },
            )

            when (segment) {
                DiscoverSegment.COURSE -> {
                    FilterSection(
                        totalCount = uiState.totalCount,
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
                        // 2026-10-05: 64dp 썸네일 + 오른쪽 빈 공간의 얇은 리스트를, 지도 타일이 주인공인
                        // 2열 갤러리로 바꿨다 — 코스를 고르는 화면에서 정작 코스의 "그림"이 가장 작았다.
                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = listState,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                start = Spacing.ScreenHorizontal,
                                end = Spacing.ScreenHorizontal,
                                top = Spacing.sm,
                                bottom = Spacing.xxl,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                        ) {
                            items(uiState.items, key = { it.routeId }) { route ->
                                RouteTile(
                                    route = route,
                                    isTogglingSave = route.routeId in uiState.togglingSaveRouteIds,
                                    onClick = { onRouteClick(route.routeId) },
                                    onToggleSaveClick = { onToggleSaveClick(route.routeId) },
                                )
                            }
                            if (uiState.isLoadingMore) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = DallimColors.Primary)
                                    }
                                }
                            }
                        }
                    }
                }
                DiscoverSegment.SOCIAL -> {
                    val socialViewModel: SocialSessionListViewModel = hiltViewModel()
                    val socialUiState by socialViewModel.uiState.collectAsStateWithLifecycle()
                    // 세션 생성(S-31) 후 돌아왔을 때 새로고침 — 이 화면(EXPLORE)은 탭 루트라
                    // navigateToTab의 popUpTo/saveState/restoreState 때문에 NavBackStackEntry의
                    // SavedStateHandle 정체성이 SocialSessionListViewModel 생성 시점과 달라질 수
                    // 있어(실측 확인됨), SavedStateHandle 플래그 릴레이 대신 화면이 다시 보일
                    // 때(RESUME)마다 무조건 새로고침하는 더 단순하고 견고한 방식을 쓴다.
                    LifecycleResumeEffect(Unit) {
                        socialViewModel.load()
                        onPauseOrDispose { }
                    }
                    SocialSessionListBody(
                        uiState = socialUiState,
                        onSessionClick = onSessionClick,
                        onCreateClick = onCreateSessionClick,
                        onRetryClick = socialViewModel::load,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 탭 루트 화면의 상단 세그먼트 — 필터 칩과 구분되도록 언더라인 탭으로 그린다(docs/04-ui-guide.md §2 위계). */
@Composable
private fun SegmentTabs(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label,
                        style = DallimTypography.Body,
                        color = if (selected) DallimColors.TextPrimary else DallimColors.TextSecondary,
                        modifier = Modifier.padding(vertical = Spacing.sm),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (selected) DallimColors.Primary else Color.Transparent),
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DallimColors.Border))
    }
}

/** 거리 칩 한 줄 + [상태 ▾][정렬 ▾] 한 줄 — 이전엔 칩 3줄(거리/상태/정렬)이 화면 1/3을 차지했다. */
@Composable
private fun FilterSection(
    totalCount: Int,
    distanceFilter: DistanceFilter,
    statusFilter: RouteStatusFilter,
    sort: SortOption,
    onDistanceFilterChange: (DistanceFilter) -> Unit,
    onStatusFilterChange: (RouteStatusFilter) -> Unit,
    onSortChange: (SortOption) -> Unit,
) {
    Column(modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.ScreenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(DistanceFilter.entries.toList()) { option ->
                DallimFilterChip(
                    label = if (option == DistanceFilter.ALL) "전체" else option.label,
                    selected = distanceFilter == option,
                    onClick = { onDistanceFilterChange(option) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.ScreenHorizontal)
                .padding(top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "코스 ${totalCount}개",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            DallimDropdownText(
                label = statusFilter.label,
                options = RouteStatusFilter.entries.map { it.label },
                onSelect = { onStatusFilterChange(RouteStatusFilter.entries[it]) },
            )
            Spacer(modifier = Modifier.width(Spacing.md))
            DallimDropdownText(
                label = sort.label,
                options = SortOption.entries.map { it.label },
                onSelect = { onSortChange(SortOption.entries[it]) },
            )
        }
    }
}

/**
 * 코스 한 칸 — 지도 타일(정사각) 위에 저장 버튼을 얹고, 이름/거리/상태를 그 아래 둔다.
 * 타일이 셀 폭을 꽉 채우므로 "코스의 그림"이 이 화면에서 가장 큰 요소가 된다(docs/04-ui-guide.md §8).
 */
@Composable
private fun RouteTile(
    route: RouteListItem,
    isTogglingSave: Boolean,
    onClick: () -> Unit,
    onToggleSaveClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Box {
            RouteThumbnailView(
                coordinates = route.thumbnailGeoJson.toGeoPoints(),
                modifier = Modifier.fillMaxWidth(),
            )
            IconButton(
                onClick = onToggleSaveClick,
                enabled = !isTogglingSave,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    imageVector = if (route.isSaved) DallimIcons.BookmarkFilled else DallimIcons.Bookmark,
                    contentDescription = if (route.isSaved) "저장 취소" else "코스 저장",
                    // 지도 위에 여러 밝기가 섮여 있어 항상 흰색으로 둔다(미저장은 반투명).
                    tint = if (route.isSaved) DallimColors.White else DallimColors.White.copy(alpha = 0.72f),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
            Text(text = route.emoji, style = DallimTypography.Body)
            Text(
                text = route.name,
                style = DallimTypography.Title3,
                color = DallimColors.TextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        Text(
            text = "${RunFormat.km(route.distanceKm)}km · 약 ${route.estimatedMinutes}분",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
            RouteStatusBadge(status = route.status.toRouteStatus())
            Text(
                text = "${route.finisherCount}명 완주",
                style = DallimTypography.Caption,
                color = DallimColors.TextTertiary,
                modifier = Modifier.padding(start = Spacing.sm),
            )
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
            segment = DiscoverSegment.COURSE,
            onSegmentChange = {},
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
            onRouteClick = {},
            onTabSelected = {},
            onCreateCourseClick = {},
            onSessionClick = {},
            onCreateSessionClick = {},
            onDistanceFilterChange = {},
            onStatusFilterChange = {},
            onSortChange = {},
            onRetryClick = {},
            onLoadNextPage = {},
            onToggleSaveClick = {},
        )
    }
}
