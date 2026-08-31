package com.dallim.app.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.home.HomeResponseBody
import com.dallim.network.home.RecentRun
import com.dallim.network.home.TodaySketch
import com.dallim.network.user.SavedRouteItem
import com.dallim.ui.components.DallimCard
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.RouteThumbnailView
import com.dallim.ui.components.SectionHeader
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
 */
@Composable
fun HomeRoute(
    onRouteClick: (routeId: String) -> Unit,
    onExploreClick: () -> Unit,
    onSeeAllSavedRoutesClick: () -> Unit,
    onDallimbookClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onRouteClick = onRouteClick,
        onExploreClick = onExploreClick,
        onSeeAllSavedRoutesClick = onSeeAllSavedRoutesClick,
        onDallimbookClick = onDallimbookClick,
        onRetryClick = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    onRouteClick: (routeId: String) -> Unit,
    onExploreClick: () -> Unit,
    onSeeAllSavedRoutesClick: () -> Unit,
    onDallimbookClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "달림", style = DallimTypography.Title1, color = DallimColors.TextPrimary)
            Row {
                IconButton(onClick = onDallimbookClick) {
                    Icon(
                        imageVector = Icons.Outlined.AutoStories,
                        contentDescription = "달림북",
                        tint = DallimColors.TextPrimary,
                    )
                }
                IconButton(onClick = onExploreClick) {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = "코스 탐색", tint = DallimColors.TextPrimary)
                }
            }
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.ScreenHorizontal),
            ) {
                HeroSection(todaySketch = uiState.home.todaySketch, onRouteClick = onRouteClick)

                SectionHeader(title = "최근 달림", modifier = Modifier.padding(top = Spacing.xl))
                RecentRunsSection(recentRuns = uiState.home.recentRuns.take(3))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xl),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(title = "저장한 코스", modifier = Modifier.padding(bottom = 0.dp))
                    DallimTextButton(text = "더보기", onClick = onSeeAllSavedRoutesClick)
                }
                SavedRoutesPreviewSection(savedRoutes = uiState.savedRoutesPreview, onRouteClick = onRouteClick)

                Box(modifier = Modifier.padding(bottom = Spacing.xxl))
            }
        }
    }
}

@Composable
private fun HeroSection(todaySketch: TodaySketch?, onRouteClick: (String) -> Unit) {
    SectionHeader(title = "오늘의 달림", modifier = Modifier.padding(top = Spacing.lg))
    if (todaySketch == null) {
        DallimCard {
            Text(
                text = "오늘 추천할 코스가 아직 없어요",
                style = DallimTypography.Body,
                color = DallimColors.TextSecondary,
            )
        }
        return
    }

    DallimCard {
        RouteThumbnailView(
            coordinates = todaySketch.thumbnailGeoJson.toGeoPoints(),
            useGradient = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.padding(top = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 코스 이름의 이모지는 콘텐츠 데이터이므로 예외적으로 허용된다 (docs/04-ui-guide.md §7).
            Text(text = todaySketch.emoji, style = DallimTypography.Title2)
            Text(
                text = todaySketch.name,
                style = DallimTypography.Title2,
                color = DallimColors.TextPrimary,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        Text(
            text = "${todaySketch.distanceKm}km · 약 ${todaySketch.estimatedMinutes}분",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        DallimPrimaryButton(
            text = "코스 보기",
            onClick = { onRouteClick(todaySketch.routeId) },
            modifier = Modifier.padding(top = Spacing.md),
        )
    }
}

@Composable
private fun RecentRunsSection(recentRuns: List<RecentRun>) {
    if (recentRuns.isEmpty()) {
        Text(
            text = "아직 달린 기록이 없어요",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
        )
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(vertical = Spacing.xs),
    ) {
        items(recentRuns) { run -> RecentRunCard(run) }
    }
}

@Composable
private fun RecentRunCard(run: RecentRun) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DallimColors.Surface)
            .padding(Spacing.md),
    ) {
        Text(text = "${run.distanceKm}km", style = DallimTypography.Title2, color = DallimColors.TextPrimary)
        Text(
            text = run.completedAt.toShortDateLabel(),
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun SavedRoutesPreviewSection(savedRoutes: List<SavedRouteItem>, onRouteClick: (String) -> Unit) {
    if (savedRoutes.isEmpty()) {
        Text(
            text = "아직 저장한 코스가 없어요",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
        )
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(vertical = Spacing.xs),
    ) {
        items(savedRoutes) { route -> SavedRoutePreviewCard(route, onClick = { onRouteClick(route.routeId) }) }
    }
}

@Composable
private fun SavedRoutePreviewCard(route: SavedRouteItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DallimColors.Surface)
            .clickable { onClick() }
            .padding(Spacing.md),
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
            text = "${route.distanceKm}km",
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
                    recentRuns = listOf(
                        RecentRun(runId = "run_101", distanceKm = 5.18, completedAt = "2026-08-20T07:32:00Z"),
                        RecentRun(runId = "run_100", distanceKm = 3.4, completedAt = "2026-08-18T07:10:00Z"),
                    ),
                ),
                savedRoutesPreview = listOf(
                    SavedRouteItem(routeId = "rt_002", name = "물고기", emoji = "🐟", distanceKm = 4.2, hasRun = false),
                ),
            ),
            onRouteClick = {},
            onExploreClick = {},
            onSeeAllSavedRoutesClick = {},
            onDallimbookClick = {},
            onRetryClick = {},
        )
    }
}
