package com.dallim.app.race.detail

import com.dallim.ui.icons.DallimIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
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
import com.dallim.ui.components.DallimSecondaryButton
import com.dallim.ui.components.BadgeTone
import com.dallim.ui.components.DallimBadge
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimTopBar
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
    onCoursePreviewClick: (raceId: String) -> Unit,
    onTrainingPlanClick: (raceId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RaceDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RaceDetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetryClick = viewModel::load,
        onToggleSaveClick = viewModel::onToggleSaveClick,
        onCoursePreviewClick = { onCoursePreviewClick(viewModel.raceId) },
        onTrainingPlanClick = { onTrainingPlanClick(viewModel.raceId) },
        modifier = modifier,
    )
}

@Composable
private fun RaceDetailScreen(
    uiState: RaceDetailUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onToggleSaveClick: () -> Unit,
    onCoursePreviewClick: () -> Unit,
    onTrainingPlanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        DallimTopBar(title = "대회 상세", onBackClick = onBackClick)

        when (uiState) {
            is RaceDetailUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
            is RaceDetailUiState.Error -> DallimErrorState(
                title = "대회 정보를 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            is RaceDetailUiState.Success -> {
                val race = uiState.race
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Column(modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal)) {
                        RaceDetailStatusChip(status = race.status, modifier = Modifier.padding(top = Spacing.md))
                        Text(
                            text = race.name,
                            style = DallimTypography.Title1,
                            color = DallimColors.TextPrimary,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                        Text(
                            text = "${race.region} \u00b7 ${race.location}",
                            style = DallimTypography.Body,
                            color = DallimColors.TextSecondary,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )

                        // 핵심 3칸 — [남은 일수 | 대회일 | 참가비]. 큰 D-day 강조색 대신 숫자를 같은 위계로.
                        Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
                            KeyFact(label = "남은 일수", value = RaceFormat.dDayLabel(race.dDay), modifier = Modifier.weight(1f))
                            KeyFact(label = "대회일", value = RaceFormat.displayDate(race.raceDate), modifier = Modifier.weight(1.6f))
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.lg), color = DallimColors.Divider)

                        InfoRow(label = "주최", value = race.organizer)
                        race.souvenir?.let { InfoRow(label = "기념품", value = it) }
                        InfoRow(label = "참가비", value = RaceFormat.feeRange(race.minFeeKrw, race.maxFeeKrw))
                        if (race.savedCount > 0) {
                            InfoRow(label = "관심", value = "${race.savedCount}명이 담았어요")
                        }

                        // 바로가기 목록 — 코스 미리 달리기(S-85, 공식 코스가 있을 때), 훈련 플랜(S-86, 담은 대회일 때만:
                        // 서버가 담지 않은 대회는 400 TRAINING_PLAN_RACE_NOT_SAVED로 거절한다).
                        val previewProgressPercent = race.previewProgressPercent
                        if (uiState.hasCourse || race.isSaved) {
                            DallimCard(modifier = Modifier.padding(top = Spacing.lg)) {
                                if (uiState.hasCourse) {
                                    ShortcutRow(
                                        title = "코스 미리 달리기",
                                        subtitle = previewProgressPercent?.let { RaceFormat.previewProgressLabel(it) }
                                            ?: "공식 코스를 구간별로 미리 달려볼 수 있어요",
                                        onClick = onCoursePreviewClick,
                                    )
                                }
                                if (uiState.hasCourse && race.isSaved) {
                                    HorizontalDivider(color = DallimColors.Border)
                                }
                                if (race.isSaved) {
                                    ShortcutRow(
                                        title = "훈련 플랜 받기",
                                        subtitle = "주차별 훈련과 코스를 자동으로 짜드려요",
                                        onClick = onTrainingPlanClick,
                                    )
                                }
                            }
                        }

                        SectionHeader(title = "종목", modifier = Modifier.padding(top = Spacing.xl))
                        race.categories.forEachIndexed { index, category ->
                            if (index > 0) HorizontalDivider(color = DallimColors.Divider)
                            CategoryRow(category)
                        }

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

                // 담은 상태면 저강조(회색) 버튼, 아니면 Primary — 화면의 주 행동은 "담기".
                if (race.isSaved) {
                    DallimSecondaryButton(
                        text = "담기 취소",
                        onClick = onToggleSaveClick,
                        enabled = !uiState.isSaving,
                        modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md),
                    )
                } else {
                    DallimPrimaryButton(
                        text = "내 대회에 담기",
                        onClick = onToggleSaveClick,
                        enabled = !uiState.isSaving,
                        modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = DallimTypography.Caption, color = DallimColors.TextSecondary)
        Text(
            text = value,
            style = DallimTypography.Title2,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Text(text = label, style = DallimTypography.Body, color = DallimColors.TextSecondary, modifier = Modifier.width(72.dp))
        Text(text = value, style = DallimTypography.Body, color = DallimColors.TextPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ShortcutRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = DallimTypography.Title3, color = DallimColors.TextPrimary)
            Text(
                text = subtitle,
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(imageVector = DallimIcons.ChevronRight, contentDescription = null, tint = DallimColors.TextTertiary)
    }
}

/** 종목 한 줄 — [종목명 + 거리 | 참가비], 아래에 정원/컷오프. 표 대신 목록으로. */
@Composable
private fun CategoryRow(category: RaceCategoryDetail) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = RaceFormat.categoryLabel(category.category),
                style = DallimTypography.Title3,
                color = DallimColors.TextPrimary,
            )
            Text(
                text = RaceFormat.distanceLabel(category.distanceKm),
                style = DallimTypography.Body,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(start = Spacing.sm).weight(1f),
            )
            Text(text = RaceFormat.feeLabel(category.feeKrw), style = DallimTypography.Title3, color = DallimColors.TextPrimary)
        }
        Text(
            text = "정원 ${RaceFormat.capacityLabel(category.capacity)} \u00b7 컷오프 ${RaceFormat.cutoffLabel(category.cutoffMinutes)}",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun RaceDetailStatusChip(status: String, modifier: Modifier = Modifier) {
    val color = when (status) {
        "OPEN" -> DallimColors.Success
        "UPCOMING" -> DallimColors.RouteVerified
        else -> DallimColors.TextSecondary
    }
    DallimBadge(
        label = RaceFormat.statusLabel(status),
        foreground = color,
        background = color.copy(alpha = 0.12f),
        modifier = modifier,
        tone = if (status == "OPEN" || status == "UPCOMING") BadgeTone.ACTIVE else BadgeTone.QUIET,
    )
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
                    isSaved = true,
                    previewProgressPercent = 62,
                ),
                hasCourse = true,
            ),
            onBackClick = {},
            onRetryClick = {},
            onToggleSaveClick = {},
            onCoursePreviewClick = {},
            onTrainingPlanClick = {},
        )
    }
}
