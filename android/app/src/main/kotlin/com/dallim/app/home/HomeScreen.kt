package com.dallim.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.dallim.app.running.RunFormat
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.home.HomeResponseBody
import com.dallim.network.home.RecentRun
import com.dallim.network.home.TodaySketch
import com.dallim.network.home.WeekSummary
import com.dallim.network.user.SavedRouteItem
import com.dallim.ui.components.DallimBottomNavigation
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimMark
import com.dallim.ui.components.DallimTab
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.RouteThumbnailView
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * S-10 홈. Hero 카드(오늘의 달림) / 최근 달림 3개 / 저장 코스 리스트 3개 섹션으로 구성한다
 * (docs/01-feature-spec.md §1.2). `GET /home`의 `continueRoutes`는 이 화면 스펙(§1.2)이
 * 명시한 3개 섹션에 없어 이번 라운드에서는 렌더링하지 않는다 — 임의 확장 방지.
 *
 * 하단 탭바 도입(01-feature-spec.md §1.0)으로 상단의 책(달림북)/돋보기(탐색) 아이콘은
 * 탭바와 기능이 중복되어 제거했다 — 그 경로는 이제 [onTabSelected]로만 이동한다.
 *
 * 타이틀 아래에는 [HomeUiState.Success.nickname]이 있을 때만 짧은 인사말을 덧붙인다 —
 * 닉네임이 없으면(조회 실패/온보딩 미완료) 기존처럼 "달림" 타이틀만 보인다.
 */
@Composable
fun HomeRoute(
    onRouteClick: (routeId: String) -> Unit,
    onFreeRunClick: () -> Unit,
    onSeeAllSavedRoutesClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onTabSelected: (DallimTab) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onRouteClick = onRouteClick,
        onFreeRunClick = onFreeRunClick,
        onSeeAllSavedRoutesClick = onSeeAllSavedRoutesClick,
        onNotificationClick = onNotificationClick,
        onTabSelected = onTabSelected,
        onRetryClick = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    onRouteClick: (routeId: String) -> Unit,
    onFreeRunClick: () -> Unit,
    onSeeAllSavedRoutesClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onTabSelected: (DallimTab) -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = DallimColors.Background,
        bottomBar = {
            DallimBottomNavigation(selectedTab = DallimTab.HOME, onTabSelected = onTabSelected)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DallimColors.Background)
                .padding(innerPadding),
        ) {
            val unreadNotificationCount = (uiState as? HomeUiState.Success)?.unreadNotificationCount ?: 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.ScreenHorizontal, end = Spacing.sm, top = Spacing.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DallimMark(modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(text = "달림", style = DallimTypography.Title1, color = DallimColors.TextPrimary)
                }
                NotificationBellButton(unreadCount = unreadNotificationCount, onClick = onNotificationClick)
            }

            when (uiState) {
                is HomeUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
                is HomeUiState.Error -> DallimErrorState(
                    title = "홈 정보를 불러오지 못했어요",
                    description = uiState.message,
                    onRetry = onRetryClick,
                    modifier = Modifier.weight(1f),
                )
                is HomeUiState.Success -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    WeekSummaryBlock(
                        summary = uiState.home.weekSummary,
                        modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal).padding(top = Spacing.lg),
                    )

                    StartBlock(
                        onStartClick = onFreeRunClick,
                        onPickCourseClick = { onTabSelected(DallimTab.EXPLORE) },
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
                    )

                    CoursesSection(
                        todaySketch = uiState.home.todaySketch,
                        savedRoutes = uiState.savedRoutesPreview,
                        onRouteClick = onRouteClick,
                        onSeeAllSavedRoutesClick = onSeeAllSavedRoutesClick,
                        modifier = Modifier.padding(top = Spacing.xl),
                    )

                    // S-56 홈 네이티브 광고 배너 (docs/달림_화면별_상세기획서_v1.3.md 236행: 최근 달림
                    // 바로 위). 로드 실패 시 자리를 차지하지 않는다.
                    HomeNativeAdBanner(
                        modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal).padding(top = Spacing.xl),
                    )

                    RecentRunsSection(
                        recentRuns = uiState.home.recentRuns.take(3),
                        modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal).padding(top = Spacing.xl),
                    )

                    Box(modifier = Modifier.padding(bottom = Spacing.xxl))
                }
            }
        }
    }
}

private val DAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")

/**
 * 이번 주 누적 거리(큰 숫자) + 월~일 점. 인사말·섹션 제목 대신 "내 숫자"를 첫 화면의 주인공으로 둔다
 * (나이키 런 클럽 류 러닝 앱 홈 구조). 완주한 요일은 Primary 채움, 오늘은 링, 나머지는 회색 점.
 */
@Composable
private fun WeekSummaryBlock(summary: WeekSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = "이번 주", style = DallimTypography.Caption, color = DallimColors.TextSecondary)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = Spacing.xs)) {
            Text(
                text = RunFormat.km(summary.distanceKm),
                style = DallimTypography.Display,
                color = DallimColors.TextPrimary,
            )
            Text(
                text = "km",
                style = DallimTypography.Title2,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DAY_LABELS.forEachIndexed { index, label ->
                val day = index + 1
                DayDot(label = label, ran = day in summary.runDays, isToday = day == summary.todayDayOfWeek)
            }
        }
    }
}

@Composable
private fun DayDot(label: String, ran: Boolean, isToday: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (ran) DallimColors.Primary else DallimColors.Border.copy(alpha = 0.6f))
                .then(if (isToday && !ran) Modifier.border(2.dp, DallimColors.Primary, CircleShape) else Modifier),
        )
        Text(
            text = label,
            style = DallimTypography.Caption,
            color = if (isToday) DallimColors.TextPrimary else DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/**
 * 자유 러닝 시작(S-20, routeId 없음)이 이 화면의 유일한 Primary 액션이다(docs/04-ui-guide.md §2).
 * 코스를 고르려면 아래 텍스트 링크로 탐색 탭으로 간다.
 */
@Composable
private fun StartBlock(onStartClick: () -> Unit, onPickCourseClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(START_HALO_SIZE)
                .clip(CircleShape)
                .background(DallimColors.PrimaryLight),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(START_BUTTON_SIZE)
                    .clip(CircleShape)
                    .background(DallimColors.Primary)
                    .clickable(onClick = onStartClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "시작", style = DallimTypography.Title1, color = DallimColors.Surface)
            }
        }
        DallimTextButton(text = "코스 고르기  \u203A", onClick = onPickCourseClick, modifier = Modifier.padding(top = Spacing.xs))
    }
}

private val START_HALO_SIZE = 168.dp
private val START_BUTTON_SIZE = 136.dp

/** 오늘의 추천 코스 + 저장한 코스를 한 줄 가로 목록으로 — 카드 껍데기 없이 썸네일과 글자만 둔다. */
@Composable
private fun CoursesSection(
    todaySketch: TodaySketch?,
    savedRoutes: List<SavedRouteItem>,
    onRouteClick: (String) -> Unit,
    onSeeAllSavedRoutesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiles = buildList {
        if (todaySketch != null) {
            add(
                CourseTile(
                    routeId = todaySketch.routeId,
                    name = todaySketch.name,
                    emoji = todaySketch.emoji,
                    caption = "오늘의 추천 \u00b7 ${RunFormat.km(todaySketch.distanceKm)}km",
                    thumbnailGeoJson = todaySketch.thumbnailGeoJson,
                ),
            )
        }
        savedRoutes
            .filter { it.routeId != todaySketch?.routeId }
            .forEach {
                add(
                    CourseTile(
                        routeId = it.routeId,
                        name = it.name,
                        emoji = it.emoji,
                        caption = "저장 \u00b7 ${RunFormat.km(it.distanceKm)}km",
                        thumbnailGeoJson = it.thumbnailGeoJson,
                    ),
                )
            }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.ScreenHorizontal),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "추천 코스", style = DallimTypography.Title2, color = DallimColors.TextPrimary)
            DallimTextButton(text = "저장한 코스", onClick = onSeeAllSavedRoutesClick)
        }
        if (tiles.isEmpty()) {
            Text(
                text = "아직 추천할 코스가 없어요",
                style = DallimTypography.Body,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.ScreenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(tiles, key = { it.routeId }) { tile -> CourseTileView(tile, onClick = { onRouteClick(tile.routeId) }) }
            }
        }
    }
}

private data class CourseTile(
    val routeId: String,
    val name: String,
    val emoji: String,
    val caption: String,
    val thumbnailGeoJson: GeoJsonLineString,
)

@Composable
private fun CourseTileView(tile: CourseTile, onClick: () -> Unit) {
    Column(modifier = Modifier.width(COURSE_TILE_WIDTH).clickable(onClick = onClick)) {
        RouteThumbnailView(coordinates = tile.thumbnailGeoJson.toGeoPoints(), modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
            // 코스 이름의 이모지는 콘텐츠 데이터이므로 예외적으로 허용된다 (docs/04-ui-guide.md §7).
            Text(text = tile.emoji, style = DallimTypography.Body)
            Text(
                text = tile.name,
                style = DallimTypography.Body,
                color = DallimColors.TextPrimary,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        Text(
            text = tile.caption,
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

private val COURSE_TILE_WIDTH = 148.dp

/** 최근 달림은 카드 대신 구분선 목록 [썸네일 | 이름·날짜 | 거리] — 같은 카드가 연달아 반복되지 않게. */
@Composable
private fun RecentRunsSection(recentRuns: List<RecentRun>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "최근 달림",
            style = DallimTypography.Title2,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        if (recentRuns.isEmpty()) {
            Text(
                text = "아직 달린 기록이 없어요. 위의 시작 버튼으로 첫 달림을 남겨보세요.",
                style = DallimTypography.Body,
                color = DallimColors.TextSecondary,
            )
            return
        }
        recentRuns.forEachIndexed { index, run ->
            if (index > 0) HorizontalDivider(color = DallimColors.Border)
            RecentRunRow(run)
        }
    }
}

@Composable
private fun RecentRunRow(run: RecentRun) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouteThumbnailView(
            coordinates = run.thumbnailGeoJson.toGeoPoints(),
            modifier = Modifier.width(56.dp),
            cornerRadius = 12.dp,
        )
        Column(modifier = Modifier.padding(start = Spacing.md).weight(1f)) {
            Text(
                text = listOfNotNull(run.emoji, run.routeName).joinToString(" "),
                style = DallimTypography.Body,
                color = DallimColors.TextPrimary,
            )
            Text(
                text = run.completedAt.toShortDateLabel(),
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        Text(
            text = "${RunFormat.km(run.distanceKm)}km",
            style = DallimTypography.Title2,
            color = DallimColors.TextPrimary,
        )
    }
}

/**
 * 홈(S-10) 상단 종 모양 아이콘 + 안 읽은 개수 배지 — 탭하면 S-46 알림 목록으로 이동한다
 * (docs/01-feature-spec.md §1.7). 배지는 안 읽은 알림이 있을 때만 보이고, 99개를 넘으면
 * "99+"로 자른다.
 */
@Composable
private fun NotificationBellButton(unreadCount: Int, onClick: () -> Unit) {
    Box {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = "알림",
                tint = DallimColors.TextPrimary,
            )
        }
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = Spacing.xs, end = Spacing.xs)
                    .clip(CircleShape)
                    .background(DallimColors.Error)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    style = DallimTypography.Caption,
                    color = DallimColors.Surface,
                )
            }
        }
    }
}

private fun GeoJsonLineString.toGeoPoints(): List<GeoPoint> =
    toLngLatPairs().map { (lng, lat) -> GeoPoint(lng = lng, lat = lat) }

private fun String.toShortDateLabel(): String = runCatching {
    OffsetDateTime.parse(this).format(DateTimeFormatter.ofPattern("MM/dd"))
}.getOrDefault(take(10))

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun HomeScreenPreview() {
    DallimTheme {
        HomeScreen(
            uiState = HomeUiState.Success(
                home = HomeResponseBody(
                    todaySketch = TodaySketch(
                        routeId = "rt_001",
                        name = "고래",
                        emoji = "🐳",
                        distanceKm = 5.1,
                        estimatedMinutes = 36,
                        thumbnailGeoJson = GeoJsonLineString(
                            coordinates = listOf(
                                listOf(127.05, 37.25),
                                listOf(127.052, 37.253),
                                listOf(127.055, 37.251),
                                listOf(127.058, 37.256),
                            ),
                        ),
                    ),
                    continueRoutes = emptyList(),
                    weekSummary = WeekSummary(distanceKm = 11.3, runCount = 3, runDays = listOf(1, 3, 4), todayDayOfWeek = 5),
                    recentRuns = listOf(
                        RecentRun(
                            runId = "run_101",
                            distanceKm = 5.18,
                            completedAt = "2026-08-20T07:32:00Z",
                            routeId = "rt_001",
                            routeName = "고래",
                            emoji = "🐳",
                            thumbnailGeoJson = GeoJsonLineString(
                                coordinates = listOf(listOf(127.05, 37.25), listOf(127.052, 37.253), listOf(127.055, 37.251)),
                            ),
                        ),
                        RecentRun(
                            runId = "run_100",
                            distanceKm = 3.4,
                            completedAt = "2026-08-18T07:10:00Z",
                            routeId = "rt_002",
                            routeName = "물고기",
                            emoji = "🐟",
                            thumbnailGeoJson = GeoJsonLineString(
                                coordinates = listOf(listOf(127.04, 37.24), listOf(127.045, 37.243), listOf(127.041, 37.248)),
                            ),
                        ),
                    ),
                ),
                savedRoutesPreview = listOf(
                    SavedRouteItem(
                        routeId = "rt_002",
                        name = "물고기",
                        emoji = "🐟",
                        distanceKm = 4.2,
                        hasRun = false,
                        thumbnailGeoJson = GeoJsonLineString(
                            coordinates = listOf(listOf(127.04, 37.24), listOf(127.045, 37.243), listOf(127.041, 37.248)),
                        ),
                    ),
                ),
                nickname = "달리는고래",
                unreadNotificationCount = 3,
            ),
            onRouteClick = {},
            onFreeRunClick = {},
            onSeeAllSavedRoutesClick = {},
            onNotificationClick = {},
            onTabSelected = {},
            onRetryClick = {},
        )
    }
}
