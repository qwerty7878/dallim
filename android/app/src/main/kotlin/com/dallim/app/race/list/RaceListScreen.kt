package com.dallim.app.race.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Bookmark
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
import com.dallim.app.race.RaceFormat
import com.dallim.network.race.RaceSummaryItem
import com.dallim.ui.components.DallimBottomNavigation
import com.dallim.ui.components.DallimCard
import com.dallim.ui.components.DallimEmptyState
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimFilterChip
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimTab
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * 대회 탭([com.dallim.app.navigation.DallimDestinations.RACE_TAB], 2026-09-16 재편) — S-80
 * 대회 캘린더의 최상위 탭 진입점. 홈(S-10)/마이(S-42)와 동일한 최상위 탭 화면 패턴(뒤로가기
 * 버튼 없음, 하단 탭바로만 진입/이탈)을 따르고 본문은 [RaceListBody]를 그대로 재사용한다
 * (2026-09-15에 잠깐 있었던 소셜 허브 세그먼트 통합은 사용자 지시로 폐기됨).
 */
@Composable
fun RaceListRoute(
    onTabSelected: (DallimTab) -> Unit,
    onRaceClick: (raceId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RaceListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = DallimColors.Background,
        bottomBar = {
            DallimBottomNavigation(selectedTab = DallimTab.RACE, onTabSelected = onTabSelected)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DallimColors.Background)
                .padding(innerPadding),
        ) {
            Text(
                text = "대회",
                style = DallimTypography.Title1,
                color = DallimColors.TextPrimary,
                modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md),
            )
            RaceListBody(
                uiState = uiState,
                onRaceClick = onRaceClick,
                onRegionFilterChange = viewModel::onRegionFilterChange,
                onCategoryFilterChange = viewModel::onCategoryFilterChange,
                onStatusFilterChange = viewModel::onStatusFilterChange,
                onRetryClick = viewModel::retry,
                onToggleSaveClick = viewModel::onToggleSaveClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * S-80 대회 캘린더 — 리스트 뷰만 구현(월별 캘린더 뷰는 이번 라운드 범위 밖,
 * docs/02-api-spec.md 16.6). [RaceListRoute](대회 탭)의 본문 — 헤더나 탭바는 그리지 않고
 * 필터+목록만 담당한다.
 */
@Composable
fun RaceListBody(
    uiState: RaceListUiState,
    onRaceClick: (raceId: String) -> Unit,
    onRegionFilterChange: (RaceRegionFilter) -> Unit,
    onCategoryFilterChange: (RaceCategoryFilter) -> Unit,
    onStatusFilterChange: (RaceStatusFilter) -> Unit,
    onRetryClick: () -> Unit,
    onToggleSaveClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background),
    ) {
        RaceFilterSection(
            regionFilter = uiState.regionFilter,
            categoryFilter = uiState.categoryFilter,
            statusFilter = uiState.statusFilter,
            onRegionFilterChange = onRegionFilterChange,
            onCategoryFilterChange = onCategoryFilterChange,
            onStatusFilterChange = onStatusFilterChange,
        )

        when {
            uiState.isLoading && uiState.items.isEmpty() -> DallimLoadingState(modifier = Modifier.weight(1f))
            uiState.errorMessage != null && uiState.items.isEmpty() -> DallimErrorState(
                title = "대회 정보를 불러오지 못했어요",
                description = uiState.errorMessage,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            uiState.items.isEmpty() -> DallimEmptyState(
                title = "이 조건에 맞는 대회가 없어요",
                description = "필터를 바꿔서 다시 찾아보세요.",
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Spacing.ScreenHorizontal,
                    end = Spacing.ScreenHorizontal,
                    top = Spacing.sm,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.ListItemGap),
            ) {
                items(uiState.items, key = { it.raceId }) { race ->
                    RaceCard(
                        race = race,
                        isTogglingSave = race.raceId in uiState.togglingSaveRaceIds,
                        onClick = { onRaceClick(race.raceId) },
                        onToggleSaveClick = { onToggleSaveClick(race.raceId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RaceFilterSection(
    regionFilter: RaceRegionFilter,
    categoryFilter: RaceCategoryFilter,
    statusFilter: RaceStatusFilter,
    onRegionFilterChange: (RaceRegionFilter) -> Unit,
    onCategoryFilterChange: (RaceCategoryFilter) -> Unit,
    onStatusFilterChange: (RaceStatusFilter) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = Spacing.sm)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.ScreenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(RaceRegionFilter.entries.toList()) { option ->
                DallimFilterChip(
                    label = option.label,
                    selected = regionFilter == option,
                    onClick = { onRegionFilterChange(option) },
                )
            }
        }
        LazyRow(
            modifier = Modifier.padding(top = Spacing.sm),
            contentPadding = PaddingValues(horizontal = Spacing.ScreenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(RaceCategoryFilter.entries.toList()) { option ->
                DallimFilterChip(
                    label = option.label,
                    selected = categoryFilter == option,
                    onClick = { onCategoryFilterChange(option) },
                )
            }
        }
        LazyRow(
            modifier = Modifier.padding(top = Spacing.sm),
            contentPadding = PaddingValues(horizontal = Spacing.ScreenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(RaceStatusFilter.entries.toList()) { option ->
                DallimFilterChip(
                    label = option.label,
                    selected = statusFilter == option,
                    onClick = { onStatusFilterChange(option) },
                )
            }
        }
    }
}

@Composable
private fun RaceCard(
    race: RaceSummaryItem,
    isTogglingSave: Boolean,
    onClick: () -> Unit,
    onToggleSaveClick: () -> Unit,
) {
    DallimCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = race.name, style = DallimTypography.Body, color = DallimColors.TextPrimary)
                Text(
                    text = "${RaceFormat.displayDate(race.raceDate)} · ${race.location}",
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            Text(
                text = RaceFormat.dDayLabel(race.dDay),
                style = DallimTypography.Title2,
                color = DallimColors.Primary,
            )
            IconButton(onClick = onToggleSaveClick, enabled = !isTogglingSave) {
                Icon(
                    imageVector = if (race.isSaved) Icons.Filled.Bookmark else Icons.Outlined.Bookmark,
                    contentDescription = if (race.isSaved) "담기 취소" else "내 대회에 담기",
                    tint = if (race.isSaved) DallimColors.Primary else DallimColors.TextSecondary,
                )
            }
        }

        Row(
            modifier = Modifier.padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            race.categories.forEach { category -> RaceCategoryBadge(label = RaceFormat.categoryLabel(category)) }
            RaceStatusChip(status = race.status)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = RaceFormat.feeRange(race.minFeeKrw, race.maxFeeKrw),
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
            )
            Text(
                text = "달림 러너 ${race.savedCount}명 참가 예정",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
            )
        }

        // S-85(코스 미리 달리기) 답사 진행률 — 공식 코스가 없는 대회는 previewProgressPercent가
        // null이라 아무것도 그리지 않는다(RaceDetailScreen과 동일한 표시 규칙).
        val previewProgressPercent = race.previewProgressPercent
        if (previewProgressPercent != null) {
            Text(
                text = RaceFormat.previewProgressLabel(previewProgressPercent),
                style = DallimTypography.Caption,
                color = DallimColors.Primary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun RaceCategoryBadge(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DallimColors.PrimaryLight)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    ) {
        Text(text = label, style = DallimTypography.Caption, color = DallimColors.Primary)
    }
}

@Composable
private fun RaceStatusChip(status: String) {
    val color = when (status) {
        "OPEN" -> DallimColors.Success
        "UPCOMING" -> DallimColors.RouteVerified
        else -> DallimColors.TextSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    ) {
        Text(text = RaceFormat.statusLabel(status), style = DallimTypography.Caption, color = color)
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun RaceListBodyPreview() {
    DallimTheme {
        RaceListBody(
            uiState = RaceListUiState(
                items = listOf(
                    RaceSummaryItem(
                        raceId = "rce_002",
                        name = "서울 하프 마라톤",
                        region = "서울",
                        location = "잠실종합운동장",
                        raceDate = "2026-11-01T08:00:00Z",
                        dDay = 55,
                        registrationStart = "2026-09-01T00:00:00Z",
                        registrationEnd = "2026-09-20T23:59:59Z",
                        status = "OPEN",
                        categories = listOf("5K", "10K", "HALF"),
                        minFeeKrw = 20000,
                        maxFeeKrw = 35000,
                        savedCount = 12,
                        isSaved = false,
                    ),
                    RaceSummaryItem(
                        raceId = "rce_008",
                        name = "과천 사슴벌레 트레일런",
                        region = "과천",
                        location = "서울대공원 산림욕장 입구",
                        raceDate = "2026-09-10T07:00:00Z",
                        dDay = 3,
                        registrationStart = "2026-06-01T00:00:00Z",
                        registrationEnd = "2026-07-15T23:59:59Z",
                        status = "CLOSED",
                        categories = listOf("TRAIL", "ULTRA"),
                        minFeeKrw = 30000,
                        maxFeeKrw = 60000,
                        savedCount = 4,
                        isSaved = true,
                    ),
                ),
                isLoading = false,
            ),
            onRaceClick = {},
            onRegionFilterChange = {},
            onCategoryFilterChange = {},
            onStatusFilterChange = {},
            onRetryClick = {},
            onToggleSaveClick = {},
        )
    }
}
