package com.dallim.app.race.trainingplan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.race.RaceFormat
import com.dallim.network.trainingplan.TrainingPlanResponseBody
import com.dallim.network.trainingplan.TrainingPlanSessionItem
import com.dallim.network.trainingplan.TrainingPlanWeekItem
import com.dallim.ui.components.DallimCard
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimTopBar
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-86 대회 목표 훈련 플랜 (docs/02-api-spec.md 19장, docs/01-feature-spec.md 1.10.3). 대회
 * 상세(S-81)에서 이 대회를 담아뒀을(isSaved) 때만 진입 카드가 보인다(RaceDetailScreen 참고).
 * 결제/구독 게이트 없음 — 로그인만 하면 누구나 사용 가능(CLAUDE.md 2026-09-18 결정).
 *
 * 세션에 매칭된 코스(`routeId`)는 이름/거리만 텍스트로 보여준다 — 이 응답엔 GeoJSON이 없어
 * [com.dallim.ui.components.RouteThumbnailView]를 채울 좌표가 없다(19.1 `matched_route_id`는
 * id/이름/거리만 조인해서 내려준다). 실루엣을 억지로 그리려고 빈 좌표를 넘기면 모든 세션이
 * 구분 안 되는 동일한 빈 박스가 되어 오히려 정보량이 떨어지므로, 제네릭 아이콘 대신 타이포그래피
 * 로만 코스를 표현한다(docs/03-design-system.md §3.2 "제네릭 아이콘 금지"는 지키되, 좌표 없이
 * 실루엣을 흉내 내는 것도 같은 취지에서 피한다).
 */
@Composable
fun TrainingPlanRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrainingPlanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TrainingPlanScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onCategorySelected = viewModel::onCategorySelected,
        onRetryFailedClick = viewModel::onRetryFailedClick,
        onRefreshClick = viewModel::onRefreshClick,
        onRetryLoadClick = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun TrainingPlanScreen(
    uiState: TrainingPlanUiState,
    onBackClick: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onRetryFailedClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onRetryLoadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .navigationBarsPadding(),
    ) {
        DallimTopBar(title = "훈련 플랜", onBackClick = onBackClick)

        when (uiState) {
            is TrainingPlanUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))

            is TrainingPlanUiState.NeedsCategory -> CategorySelectionContent(
                uiState = uiState,
                onCategorySelected = onCategorySelected,
                modifier = Modifier.weight(1f),
            )

            is TrainingPlanUiState.Generating -> GeneratingContent(modifier = Modifier.weight(1f))

            is TrainingPlanUiState.TimedOut -> DallimErrorState(
                title = "플랜 생성이 예상보다 오래 걸리고 있어요",
                description = "잠시 후 새로고침해서 다시 확인해주세요.",
                onRetry = onRefreshClick,
                modifier = Modifier.weight(1f),
            )

            is TrainingPlanUiState.Failed -> DallimErrorState(
                title = "플랜을 만들지 못했어요",
                description = "잠시 후 다시 시도해주세요.",
                onRetry = onRetryFailedClick,
                modifier = Modifier.weight(1f),
            )

            is TrainingPlanUiState.Error -> DallimErrorState(
                title = "훈련 플랜을 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryLoadClick,
                modifier = Modifier.weight(1f),
            )

            is TrainingPlanUiState.Ready -> ReadyContent(plan = uiState.plan, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun GeneratingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = DallimColors.Primary)
        Text(
            text = "플랜을 만들고 있어요...",
            style = DallimTypography.Body,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            text = "이력 분석과 코스 매칭까지 최대 30초 정도 걸릴 수 있어요.",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun CategorySelectionContent(
    uiState: TrainingPlanUiState.NeedsCategory,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(Spacing.ScreenHorizontal)) {
        Text(
            text = "어떤 종목을 준비하시나요?",
            style = DallimTypography.Title2,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        // 최초 진입 시 uiState.errorMessage는 서버가 준 TRAINING_PLAN_CATEGORY_REQUIRED 메시지라
        // 아래 기본 안내문과 뜻이 겹친다 — 하나만 보여준다(기본 안내문은 그 메시지가 없을 때의
        // 폴백일 뿐).
        Text(
            text = uiState.errorMessage ?: "이 대회는 종목이 여러 개예요. 목표 종목을 골라주세요.",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )

        Row(
            modifier = Modifier.padding(top = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            uiState.categories.forEach { category ->
                CategoryChip(
                    label = RaceFormat.categoryLabel(category),
                    enabled = !uiState.isSubmitting,
                    onClick = { onCategorySelected(category) },
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .wrapContentWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DallimColors.PrimaryLight)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(text = label, style = DallimTypography.Body, color = DallimColors.Primary)
    }
}

@Composable
private fun ReadyContent(plan: TrainingPlanResponseBody, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal)) {
                Text(
                    text = "${RaceFormat.categoryLabel(plan.category)} 목표 훈련 플랜",
                    style = DallimTypography.Title2,
                    color = DallimColors.TextPrimary,
                    modifier = Modifier.padding(top = Spacing.lg),
                )

                val comment = plan.comment
                if (comment != null) {
                    CommentCard(comment = comment, modifier = Modifier.padding(top = Spacing.md))
                }
            }

            // 이번 주(1주차)가 위로 오는 시간순 — 서버 응답이 이미 weekNumber 오름차순으로 온다
            // (com.dallim.trainingplan.TrainingPlanService.toResponse의 toSortedMap), 진행 방향과
            // 같은 순서라 재정렬하지 않는다.
            plan.weeks.forEach { week ->
                WeekCard(
                    week = week,
                    modifier = Modifier.padding(
                        start = Spacing.ScreenHorizontal,
                        end = Spacing.ScreenHorizontal,
                        top = Spacing.lg,
                    ),
                )
            }

            Box(modifier = Modifier.padding(bottom = Spacing.xl))
        }

        // 컴플라이언스 고지 — 화면 하단에 상시 노출(작업 브리핑 지시). 스크롤 영역 밖에 고정해서
        // 주차가 많아도 항상 보이게 한다.
        val disclaimer = plan.disclaimer
        if (disclaimer != null) {
            HorizontalDivider(color = DallimColors.Border)
            Text(
                text = disclaimer,
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DallimColors.Surface)
                    .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.sm),
            )
        }
    }
}

@Composable
private fun CommentCard(comment: String, modifier: Modifier = Modifier) {
    DallimCard(modifier = modifier) {
        Text(text = "코치의 한마디", style = DallimTypography.Caption, color = DallimColors.Primary)
        Text(
            text = comment,
            style = DallimTypography.Body,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun WeekCard(week: TrainingPlanWeekItem, modifier: Modifier = Modifier) {
    DallimCard(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "${week.weekNumber}주차", style = DallimTypography.Title2, color = DallimColors.TextPrimary)
            Text(
                text = "이번 주 목표 ${RaceFormat.distanceLabel(week.weeklyTargetDistanceKm)}",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
            )
        }

        week.sessions.sortedBy { it.sessionIndex }.forEachIndexed { index, session ->
            if (index > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm), color = DallimColors.Border)
            } else {
                Box(modifier = Modifier.padding(top = Spacing.sm))
            }
            SessionRow(session = session)
        }
    }
}

@Composable
private fun SessionRow(session: TrainingPlanSessionItem, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SessionTypeBadge(type = session.type)
            if (session.targetDistanceKm != null) {
                Text(
                    text = RaceFormat.distanceLabel(session.targetDistanceKm),
                    style = DallimTypography.Body,
                    color = DallimColors.TextPrimary,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }

        // routeId가 있으면 매칭된 코스를 이름/거리 텍스트로 보여준다 — 클래스 문서 참고
        // (GeoJSON이 없어 RouteThumbnailView를 채울 좌표가 없다).
        if (session.routeId != null && session.routeName != null) {
            Text(
                text = "매칭된 코스 · ${session.routeName}" +
                    (session.routeDistanceKm?.let { " (${RaceFormat.distanceLabel(it)})" } ?: ""),
                style = DallimTypography.Caption,
                color = DallimColors.Primary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun SessionTypeBadge(type: String, modifier: Modifier = Modifier) {
    val (label, color) = when (type) {
        "LONG_RUN" -> "롱런" to DallimColors.Primary
        "TEMPO" -> "템포" to DallimColors.RouteVerified
        "INTERVAL" -> "인터벌" to DallimColors.RouteUnderReview
        "REST" -> "휴식" to DallimColors.TextSecondary
        else -> type to DallimColors.TextSecondary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    ) {
        Text(text = label, style = DallimTypography.Caption, color = color)
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun TrainingPlanScreenReadyPreview() {
    DallimTheme {
        TrainingPlanScreen(
            uiState = TrainingPlanUiState.Ready(
                plan = TrainingPlanResponseBody(
                    planId = "tp_a1b2c3d4",
                    raceId = "rce_002",
                    category = "HALF",
                    status = "READY",
                    weeks = listOf(
                        TrainingPlanWeekItem(
                            weekNumber = 1,
                            weeklyTargetDistanceKm = 7.6,
                            sessions = listOf(
                                TrainingPlanSessionItem(
                                    sessionIndex = 0,
                                    type = "LONG_RUN",
                                    targetDistanceKm = 5.1,
                                    routeId = "rt_003",
                                    routeName = "한강 러닝",
                                    routeDistanceKm = 5.0,
                                ),
                                TrainingPlanSessionItem(
                                    sessionIndex = 1,
                                    type = "TEMPO",
                                    targetDistanceKm = 2.5,
                                    routeId = "rt_010",
                                    routeName = "동네 한바퀴",
                                    routeDistanceKm = 2.4,
                                ),
                                TrainingPlanSessionItem(sessionIndex = 2, type = "REST"),
                            ),
                        ),
                        TrainingPlanWeekItem(
                            weekNumber = 2,
                            weeklyTargetDistanceKm = 9.0,
                            sessions = listOf(
                                TrainingPlanSessionItem(
                                    sessionIndex = 0,
                                    type = "LONG_RUN",
                                    targetDistanceKm = 6.0,
                                    routeId = "rt_003",
                                    routeName = "한강 러닝",
                                    routeDistanceKm = 5.0,
                                ),
                                TrainingPlanSessionItem(sessionIndex = 1, type = "INTERVAL", targetDistanceKm = 3.0),
                                TrainingPlanSessionItem(sessionIndex = 2, type = "REST"),
                            ),
                        ),
                    ),
                    comment = "완만하게 시작해서 대회 전 2주는 가볍게 조절했어요. 이번 주는 부담 없이 시작해봐요!",
                    disclaimer = "이 훈련 플랜은 일반적인 러닝 코칭 통념에 기반해 자동 생성된 참고용 콘텐츠이며 의학적 조언이 아닙니다. 통증이나 몸 상태 이상이 느껴지면 즉시 훈련을 중단하고 전문가와 상담하세요.",
                    errorMessage = null,
                ),
            ),
            onBackClick = {},
            onCategorySelected = {},
            onRetryFailedClick = {},
            onRefreshClick = {},
            onRetryLoadClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun TrainingPlanScreenGeneratingPreview() {
    DallimTheme {
        TrainingPlanScreen(
            uiState = TrainingPlanUiState.Generating(category = "HALF"),
            onBackClick = {},
            onCategorySelected = {},
            onRetryFailedClick = {},
            onRefreshClick = {},
            onRetryLoadClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun TrainingPlanScreenNeedsCategoryPreview() {
    DallimTheme {
        TrainingPlanScreen(
            uiState = TrainingPlanUiState.NeedsCategory(categories = listOf("5K", "10K", "HALF", "FULL")),
            onBackClick = {},
            onCategorySelected = {},
            onRetryFailedClick = {},
            onRefreshClick = {},
            onRetryLoadClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun TrainingPlanScreenFailedPreview() {
    DallimTheme {
        TrainingPlanScreen(
            uiState = TrainingPlanUiState.Failed(category = "HALF", errorMessage = "OpenAI timeout"),
            onBackClick = {},
            onCategorySelected = {},
            onRetryFailedClick = {},
            onRefreshClick = {},
            onRetryLoadClick = {},
        )
    }
}
