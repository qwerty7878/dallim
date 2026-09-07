package com.dallim.app.race.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
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
import com.dallim.app.race.RaceFormat
import com.dallim.network.race.RaceCategoryDetail
import com.dallim.network.race.RaceDetailResponseBody
import com.dallim.ui.components.DallimCard
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.SectionHeader
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-81 대회 상세 — 종목별(거리/참가비/정원/컷오프) 표 + 담기 토글
 * (docs/02-api-spec.md 16장, docs/달림_화면별_상세기획서_v1.3.md PART 3-H). 접수하러 가기 외부
 * 링크/목표 D-day 카드/진행률/"이 대회 준비하는 사람들"/신고 기능은 16.6에 범위 밖으로 명시돼
 * 있어 만들지 않는다 — RaceListScreen(S-80)과 동일한 "이번 라운드 범위" 원칙.
 */
@Composable
fun RaceDetailRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RaceDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RaceDetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetryClick = viewModel::load,
        onToggleSaveClick = viewModel::onToggleSaveClick,
        modifier = modifier,
    )
}

@Composable
private fun RaceDetailScreen(
    uiState: RaceDetailUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onToggleSaveClick: () -> Unit,
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
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = DallimColors.TextPrimary,
                )
            }
            Text(text = "대회 상세", style = DallimTypography.Title1, color = DallimColors.TextPrimary)
        }

        when (uiState) {
            is RaceDetailUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
            is RaceDetailUiState.Error -> DallimErrorState(
                title = "대회 정보를 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            is RaceDetailUiState.Success -> {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Column(modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal)) {
                        Row(
                            modifier = Modifier.padding(top = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RaceDetailStatusChip(status = uiState.race.status)
                            Text(
                                text = RaceFormat.dDayLabel(uiState.race.dDay),
                                style = DallimTypography.Title2,
                                color = DallimColors.Primary,
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }
                        Text(
                            text = uiState.race.name,
                            style = DallimTypography.Title1,
                            color = DallimColors.TextPrimary,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                        Text(
                            text = "${uiState.race.region} · ${uiState.race.location}",
                            style = DallimTypography.Body,
                            color = DallimColors.TextSecondary,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                        Text(
                            text = RaceFormat.displayDate(uiState.race.raceDate),
                            style = DallimTypography.Caption,
                            color = DallimColors.TextSecondary,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Spacing.lg),
                            color = DallimColors.Border,
                        )

                        LabeledRow(label = "주최", value = uiState.race.organizer)
                        val souvenir = uiState.race.souvenir
                        if (souvenir != null) {
                            LabeledRow(label = "기념품", value = souvenir, modifier = Modifier.padding(top = Spacing.sm))
                        }
                        LabeledRow(
                            label = "참가비",
                            value = RaceFormat.feeRange(uiState.race.minFeeKrw, uiState.race.maxFeeKrw),
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                        Text(
                            text = "달림 러너 ${uiState.race.savedCount}명 참가 예정",
                            style = DallimTypography.Caption,
                            color = DallimColors.TextSecondary,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )

                        SectionHeader(
                            title = "종목별 안내",
                            modifier = Modifier.padding(top = Spacing.xl),
                        )
                        CategoryTable(categories = uiState.race.categories)

                        if (uiState.saveErrorMessage != null) {
                            Text(
                                text = uiState.saveErrorMessage,
                                style = DallimTypography.Caption,
                                color = DallimColors.Error,
                                modifier = Modifier.padding(top = Spacing.md),
                            )
                        }

                        Box(modifier = Modifier.padding(bottom = Spacing.xxl))
                    }
                }

                DallimPrimaryButton(
                    text = if (uiState.race.isSaved) "담기 취소" else "담기",
                    onClick = onToggleSaveClick,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun LabeledRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = DallimTypography.Body, color = DallimColors.TextSecondary)
        Text(text = value, style = DallimTypography.Body, color = DallimColors.TextPrimary)
    }
}

@Composable
private fun CategoryTable(categories: List<RaceCategoryDetail>, modifier: Modifier = Modifier) {
    DallimCard(modifier = modifier.padding(top = Spacing.md)) {
        CategoryTableRow(
            category = "종목",
            distance = "거리",
            fee = "참가비",
            capacity = "정원",
            cutoff = "컷오프",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
        )
        categories.forEachIndexed { index, category ->
            HorizontalDivider(
                modifier = Modifier.padding(vertical = Spacing.sm),
                color = DallimColors.Border,
            )
            CategoryTableRow(
                category = RaceFormat.categoryLabel(category.category),
                distance = RaceFormat.distanceLabel(category.distanceKm),
                fee = RaceFormat.feeLabel(category.feeKrw),
                capacity = RaceFormat.capacityLabel(category.capacity),
                cutoff = RaceFormat.cutoffLabel(category.cutoffMinutes),
                style = DallimTypography.Body,
                color = DallimColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun CategoryTableRow(
    category: String,
    distance: String,
    fee: String,
    capacity: String,
    cutoff: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = category, style = style, color = color, modifier = Modifier.weight(1f))
        Text(text = distance, style = style, color = color, modifier = Modifier.weight(1f))
        Text(text = fee, style = style, color = color, modifier = Modifier.weight(1.2f))
        Text(text = capacity, style = style, color = color, modifier = Modifier.weight(0.8f))
        Text(text = cutoff, style = style, color = color, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RaceDetailStatusChip(status: String) {
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
private fun RaceDetailScreenPreview() {
    DallimTheme {
        RaceDetailScreen(
            uiState = RaceDetailUiState.Success(
                race = RaceDetailResponseBody(
                    raceId = "rce_002",
                    name = "서울 하프 마라톤",
                    region = "서울",
                    location = "잠실종합운동장",
                    raceDate = "2026-11-01T08:00:00Z",
                    dDay = 55,
                    registrationStart = "2026-09-01T00:00:00Z",
                    registrationEnd = "2026-09-20T23:59:59Z",
                    status = "OPEN",
                    organizer = "서울시체육회",
                    souvenir = "기능성 티셔츠, 완주 메달",
                    categories = listOf(
                        RaceCategoryDetail(category = "5K", distanceKm = 5.0, feeKrw = 20000, capacity = 500, cutoffMinutes = null),
                        RaceCategoryDetail(category = "10K", distanceKm = 10.0, feeKrw = 25000, capacity = 500, cutoffMinutes = 90),
                        RaceCategoryDetail(category = "HALF", distanceKm = 21.0975, feeKrw = 35000, capacity = 300, cutoffMinutes = 180),
                    ),
                    minFeeKrw = 20000,
                    maxFeeKrw = 35000,
                    savedCount = 12,
                    isSaved = false,
                ),
            ),
            onBackClick = {},
            onRetryClick = {},
            onToggleSaveClick = {},
        )
    }
}
