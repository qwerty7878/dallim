package com.dallim.app.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
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
import com.dallim.network.user.SavedRouteItem
import com.dallim.ui.components.DallimBottomNavigation
import com.dallim.ui.components.DallimCard
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimMark
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimSecondaryButton
import com.dallim.ui.components.DallimTab
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
            // 프로필 설정을 마친 사용자만 닉네임을 받아온다 — 실패/미설정 시 null로 흡수돼
            // 인사말 없이 기존 "달림" 타이틀만 보인다 (HomeViewModel KDoc 참고).
            val nickname = (uiState as? HomeUiState.Success)?.nickname
            val unreadNotificationCount = (uiState as? HomeUiState.Success)?.unreadNotificationCount ?: 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Spacing.ScreenHorizontal,
                        end = Spacing.sm,
                        top = Spacing.md,
                        bottom = if (nickname != null) Spacing.xs else Spacing.md,
                    ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DallimMark(modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = "달림",
                        style = DallimTypography.Title1,
                        color = DallimColors.TextPrimary,
                    )
                }
                NotificationBellButton(unreadCount = unreadNotificationCount, onClick = onNotificationClick)
            }
            if (nickname != null) {
                Text(
                    text = "${nickname}님, 오늘도 달려볼까요?",
                    style = DallimTypography.Body,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(
                        start = Spacing.ScreenHorizontal,
                        end = Spacing.ScreenHorizontal,
                        bottom = Spacing.md,
                    ),
                )
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
                    HeroSection(
                        todaySketch = uiState.home.todaySketch,
                        onRouteClick = onRouteClick,
                        onFreeRunClick = onFreeRunClick,
                    )

                    // S-56 홈 네이티브 광고 배너 (docs/달림_화면별_상세기획서_v1.3.md 236행 "5번째
                    // 블록 아래에만"). v1.3 문서의 홈 블록 순서는 [..., 진행 중 미션(5), 최근 달림(6),
                    // Native Ad(7)]이지만 이 화면엔 아직 진행 중 미션 블록이 없다 — 광고가 "최근 달림
                    // 바로 위"라는 v1.3의 상대 위치를 그대로 지켜, 현재 구현에서 최근 달림 바로 앞
                    // 블록인 오늘의 달림(Hero)과 최근 달림 사이에 넣는다. 로드 실패 시 자리를
                    // 전혀 차지하지 않으므로 실패해도 이 위 여백이 중복되지 않는다.
                    HomeNativeAdBanner(modifier = Modifier.padding(top = Spacing.xl))

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

private val HERO_THUMBNAIL_WIDTH = 120.dp

@Composable
private fun HeroSection(todaySketch: TodaySketch?, onRouteClick: (String) -> Unit, onFreeRunClick: () -> Unit) {
    SectionHeader(title = "오늘의 달림", modifier = Modifier.padding(top = Spacing.lg))
    if (todaySketch == null) {
        DallimCard {
            Text(
                text = "오늘 추천할 코스가 아직 없어요",
                style = DallimTypography.Body,
                color = DallimColors.TextSecondary,
            )
        }
        DallimSecondaryButton(
            text = "코스 없이 바로 달리기",
            onClick = onFreeRunClick,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        return
    }

    // 썸네일이 카드 가로 전체를 차지해 첫 화면의 절반 이상을 먹던 것을 [썸네일 | 텍스트블록] 가로
    // 배치로 줄였다(docs/04-ui-guide.md §5 리스트형 기본 구조, §1 "여백을 두려워하지 말 것").
    DallimCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RouteThumbnailView(
                coordinates = todaySketch.thumbnailGeoJson.toGeoPoints(),
                useGradient = true,
                modifier = Modifier.width(HERO_THUMBNAIL_WIDTH),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    text = "${RunFormat.km(todaySketch.distanceKm)}km · 약 ${todaySketch.estimatedMinutes}분",
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
    }
    // 자유 러닝(2026-09-26) 진입점 — 코스를 고르지 않고 바로 시작. 주 CTA("코스 보기")와 구분되는 보조 버튼.
    DallimSecondaryButton(
        text = "코스 없이 바로 달리기",
        onClick = onFreeRunClick,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@Composable
private fun RecentRunsSection(recentRuns: List<RecentRun>) {
    if (recentRuns.isEmpty()) {
        DallimCard {
            Text(text = "아직 달린 기록이 없어요", style = DallimTypography.Body, color = DallimColors.TextPrimary)
            Text(
                text = "코스를 하나 골라 오늘 첫 달림을 기록해보세요.",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(vertical = Spacing.xs),
    ) {
        items(recentRuns) { run -> RecentRunCard(run) }
    }
}

/**
 * 최근 달림도 "코스 카드"라 [RouteThumbnailView]가 필수다(docs/03-design-system.md §3.2) —
 * 2026-09-26까지는 `GET /home`의 `recentRuns`에 route 정보가 없어 거리/날짜만 보이는 텍스트
 * 전용 카드였다(design system 규칙 위반 상태). 백엔드가 route를 조인해 내려주도록 고쳐 Hero
 * 카드와 같은 [썸네일 → 이모지+이름 → 캡션] 구성으로 맞췄다.
 */
@Composable
private fun RecentRunCard(run: RecentRun) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DallimColors.Surface)
            .padding(Spacing.sm),
    ) {
        RouteThumbnailView(coordinates = run.thumbnailGeoJson.toGeoPoints(), modifier = Modifier.fillMaxWidth())
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = Spacing.sm),
        ) {
            // 코스 이름의 이모지는 콘텐츠 데이터이므로 예외적으로 허용된다 (docs/04-ui-guide.md §7).
            Text(text = run.emoji, style = DallimTypography.Body)
            Text(
                text = run.routeName,
                style = DallimTypography.Body,
                color = DallimColors.TextPrimary,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        Text(
            text = "${RunFormat.km(run.distanceKm)}km · ${run.completedAt.toShortDateLabel()}",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun SavedRoutesPreviewSection(savedRoutes: List<SavedRouteItem>, onRouteClick: (String) -> Unit) {
    if (savedRoutes.isEmpty()) {
        DallimCard {
            Text(text = "아직 저장한 코스가 없어요", style = DallimTypography.Body, color = DallimColors.TextPrimary)
            Text(
                text = "탐색에서 마음에 드는 코스를 저장해보세요.",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(vertical = Spacing.xs),
    ) {
        items(savedRoutes) { route -> SavedRoutePreviewCard(route, onClick = { onRouteClick(route.routeId) }) }
    }
}

/** [RecentRunCard]와 동일한 이유로 썸네일을 필수로 쓴다(docs/03-design-system.md §3.2). */
@Composable
private fun SavedRoutePreviewCard(route: SavedRouteItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DallimColors.Surface)
            .clickable { onClick() }
            .padding(Spacing.sm),
    ) {
        RouteThumbnailView(coordinates = route.thumbnailGeoJson.toGeoPoints(), modifier = Modifier.fillMaxWidth())
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = Spacing.sm),
        ) {
            Text(text = route.emoji, style = DallimTypography.Body)
            Text(
                text = route.name,
                style = DallimTypography.Body,
                color = DallimColors.TextPrimary,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        Text(
            text = "${RunFormat.km(route.distanceKm)}km",
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
