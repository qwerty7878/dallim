package com.dallim.app.career.shelf

import com.dallim.ui.icons.DallimIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.career.RaceRecordFormat
import com.dallim.network.racerecord.RaceRecordItem
import com.dallim.ui.components.DallimCard
import com.dallim.ui.components.DallimEmptyState
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.PbBadge
import com.dallim.ui.components.UnverifiedBadge
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-91 완주 메달 선반 — docs/달림_화면별_상세기획서_v1.3.md PART 3-H "S-82" 중 메달 선반
 * 부분만 이번 라운드 범위(대회 캘린더/목표 대회 D-day는 SPEC 있음에도 API가 없어 범위 밖 —
 * docs/02-api-spec.md 15.7). 종목별 PB 하이라이트 → 연도별 카드 그리드 순으로 배치한다.
 *
 * [onAddClick]은 로딩/에러/빈 상태와 무관하게 항상 눌러야 해서(S-83 "S-82 [+ 이력 추가]") 상단
 * 바 우측에 고정 아이콘 버튼으로 둔다 — 본문 상태에 매이지 않는다.
 *
 * `LaunchedEffect(Unit)`으로 [MedalShelfViewModel.load]를 호출한다 — S-90(등록/수정/삭제)에서
 * 뒤로 돌아왔을 때도 이 destination의 content 람다가 다시 컴포지션에 들어오면서 함께 다시
 * 실행되므로, 별도의 SavedStateHandle 결과 플래그 없이도 항상 최신 목록을 보여준다.
 */
@Composable
fun MedalShelfRoute(
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onItemClick: (raceRecordId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedalShelfViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    MedalShelfScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onAddClick = onAddClick,
        onItemClick = onItemClick,
        onRetryClick = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun MedalShelfScreen(
    uiState: MedalShelfUiState,
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onItemClick: (String) -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = DallimIcons.ArrowLeft,
                    contentDescription = "뒤로가기",
                    tint = DallimColors.TextPrimary,
                )
            }
            Text(
                text = "완주 메달 선반",
                style = DallimTypography.Title1,
                color = DallimColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAddClick) {
                Icon(imageVector = DallimIcons.Plus, contentDescription = "이력 추가", tint = DallimColors.Primary)
            }
        }

        when (uiState) {
            is MedalShelfUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
            is MedalShelfUiState.Error -> DallimErrorState(
                title = "완주 이력을 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            is MedalShelfUiState.Success -> if (uiState.items.isEmpty()) {
                DallimEmptyState(
                    title = "아직 비어 있어요",
                    description = "대회 이력을 추가해보세요.",
                    actionText = "+ 이력 추가",
                    onActionClick = onAddClick,
                    modifier = Modifier.weight(1f),
                )
            } else {
                MedalShelfContent(items = uiState.items, onItemClick = onItemClick, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 연도 헤더/2열 카드 묶음을 하나의 `LazyColumn`으로 평탄화하기 위한 화면 전용 행 단위. */
private sealed interface ShelfRow {
    data class YearHeader(val year: Int) : ShelfRow
    data class Cards(val items: List<RaceRecordItem>) : ShelfRow
}

private fun buildShelfRows(items: List<RaceRecordItem>): List<ShelfRow> {
    // 서버가 이미 연도 내림차순(동일 연도는 최신 등록순)으로 내려주므로 groupBy의 순서 보존만 신뢰한다.
    val rows = mutableListOf<ShelfRow>()
    items.groupBy { it.year }.forEach { (year, yearItems) ->
        rows += ShelfRow.YearHeader(year)
        yearItems.chunked(2).forEach { chunk -> rows += ShelfRow.Cards(chunk) }
    }
    return rows
}

@Composable
private fun MedalShelfContent(
    items: List<RaceRecordItem>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pbItems = items.filter { it.isPb }
    val rows = buildShelfRows(items)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md),
    ) {
        if (pbItems.isNotEmpty()) {
            item {
                Text(
                    text = "종목별 PB",
                    style = DallimTypography.Title2,
                    color = DallimColors.TextPrimary,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = PaddingValues(bottom = Spacing.xl),
                ) {
                    items(pbItems, key = { "pb_${it.id}" }) { item -> PbHighlightCard(item = item, onClick = { onItemClick(item.id) }) }
                }
            }
        }

        items(rows) { row ->
            when (row) {
                is ShelfRow.YearHeader -> Text(
                    text = "${row.year}년",
                    style = DallimTypography.Title2,
                    color = DallimColors.TextPrimary,
                    modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm),
                )

                is ShelfRow.Cards -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    row.items.forEach { item ->
                        RaceRecordCard(
                            item = item,
                            onClick = { onItemClick(item.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 홀수 개일 때 마지막 줄의 카드가 폭 전체로 늘어나지 않도록 빈 칸을 채운다.
                    if (row.items.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RaceRecordCard(item: RaceRecordItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DallimCard(onClick = onClick, modifier = modifier) {
        Text(
            text = item.raceName,
            style = DallimTypography.Body,
            color = DallimColors.TextPrimary,
            maxLines = 2,
        )
        Text(
            text = "${RaceRecordFormat.categoryLabel(item.category)} · ${item.year}",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        val recordTime = RaceRecordFormat.recordTime(item.recordSeconds)
        if (recordTime != null) {
            Text(
                text = recordTime,
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        Row(modifier = Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (item.isPb) PbBadge()
            UnverifiedBadge()
        }
    }
}

@Composable
private fun PbHighlightCard(item: RaceRecordItem, onClick: () -> Unit) {
    DallimCard(onClick = onClick, modifier = Modifier.width(160.dp)) {
        Text(
            text = RaceRecordFormat.categoryLabel(item.category),
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
        )
        Text(
            text = RaceRecordFormat.recordTime(item.recordSeconds) ?: "기록 없음",
            style = DallimTypography.Title2,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            text = item.raceName,
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            maxLines = 1,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        PbBadge(modifier = Modifier.padding(top = Spacing.sm))
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun MedalShelfEmptyPreview() {
    DallimTheme {
        MedalShelfScreen(
            uiState = MedalShelfUiState.Success(items = emptyList()),
            onBackClick = {},
            onAddClick = {},
            onItemClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun MedalShelfFilledPreview() {
    DallimTheme {
        MedalShelfScreen(
            uiState = MedalShelfUiState.Success(
                items = listOf(
                    RaceRecordItem(
                        id = "race_1",
                        raceName = "2026 서울 하프마라톤",
                        category = "HALF",
                        distanceKm = 21.0975,
                        year = 2026,
                        recordSeconds = 6300,
                        recordType = "NET",
                        bibNumber = "A1234",
                        memo = null,
                        verified = false,
                        isPb = true,
                        paceSuggestion = "PACE_6_7",
                        createdAt = "2026-09-06T09:00:00Z",
                    ),
                    RaceRecordItem(
                        id = "race_2",
                        raceName = "2025 춘천마라톤",
                        category = "FULL",
                        distanceKm = 42.195,
                        year = 2025,
                        recordSeconds = 16200,
                        recordType = "GROSS",
                        bibNumber = null,
                        memo = null,
                        verified = false,
                        isPb = true,
                        paceSuggestion = "PACE_6_7",
                        createdAt = "2025-10-20T09:00:00Z",
                    ),
                    RaceRecordItem(
                        id = "race_3",
                        raceName = "2025 동네 5K",
                        category = "5K",
                        distanceKm = 5.0,
                        year = 2025,
                        recordSeconds = null,
                        recordType = null,
                        bibNumber = null,
                        memo = null,
                        verified = false,
                        isPb = false,
                        paceSuggestion = null,
                        createdAt = "2025-05-01T09:00:00Z",
                    ),
                ),
            ),
            onBackClick = {},
            onAddClick = {},
            onItemClick = {},
            onRetryClick = {},
        )
    }
}
