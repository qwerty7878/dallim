package com.dallim.app.running.result

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.run.RunDetailResponseBody
import com.dallim.ui.components.DallimCard
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimFilterChip
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimSecondaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.RunResultCanvas
import com.dallim.ui.components.RunStatusBadge
import com.dallim.ui.components.SectionHeader
import com.dallim.ui.components.toRunStatus
import com.dallim.app.running.RunFormat
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-25 달림 결과 (docs/01-feature-spec.md §1.3, docs/04-ui-guide.md §8) — GPS 그림을
 * "액자에 담긴 작품처럼" Path-drawing 애니메이션으로 재생하고, 서버가 최종 계산한 지표를 보여준다.
 * "굿즈"는 수익화 기능으로 MVP1 범위 밖(CLAUDE.md)이라 액션은 공유/저장 두 개만 둔다.
 */
@Composable
fun RunResultRoute(
    onShareClick: (runId: String) -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RunResultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedFeedbackTags by viewModel.selectedFeedbackTags.collectAsStateWithLifecycle()
    val feedbackTagsSubmitted by viewModel.feedbackTagsSubmitted.collectAsStateWithLifecycle()
    val registerRouteState by viewModel.registerRouteState.collectAsStateWithLifecycle()

    RunResultScreen(
        uiState = uiState,
        selectedFeedbackTags = selectedFeedbackTags,
        feedbackTagsSubmitted = feedbackTagsSubmitted,
        registerRouteState = registerRouteState,
        onFeedbackTagToggle = viewModel::toggleFeedbackTag,
        onFeedbackTagsSubmit = viewModel::submitFeedbackTags,
        onRegisterRouteStartClick = viewModel::onRegisterRouteStartClick,
        onRegisterRouteNameChange = viewModel::onRegisterRouteNameChange,
        onRegisterRouteEmojiSelect = viewModel::onRegisterRouteEmojiSelect,
        onRegisterRouteCancel = viewModel::onRegisterRouteCancel,
        onRegisterRouteConfirm = viewModel::onRegisterRouteConfirm,
        onShareClick = { onShareClick(viewModel.runId) },
        onDoneClick = onDoneClick,
        onRetryClick = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun RunResultScreen(
    uiState: RunResultUiState,
    selectedFeedbackTags: Set<String>,
    feedbackTagsSubmitted: Boolean,
    registerRouteState: RegisterRouteUiState,
    onFeedbackTagToggle: (String) -> Unit,
    onFeedbackTagsSubmit: () -> Unit,
    onRegisterRouteStartClick: () -> Unit,
    onRegisterRouteNameChange: (String) -> Unit,
    onRegisterRouteEmojiSelect: (String) -> Unit,
    onRegisterRouteCancel: () -> Unit,
    onRegisterRouteConfirm: () -> Unit,
    onShareClick: () -> Unit,
    onDoneClick: () -> Unit,
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
        when (uiState) {
            is RunResultUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
            is RunResultUiState.Error -> DallimErrorState(
                title = "결과를 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            is RunResultUiState.Success -> {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    ResultContent(
                        run = uiState.run,
                        selectedFeedbackTags = selectedFeedbackTags,
                        feedbackTagsSubmitted = feedbackTagsSubmitted,
                        registerRouteState = registerRouteState,
                        onFeedbackTagToggle = onFeedbackTagToggle,
                        onFeedbackTagsSubmit = onFeedbackTagsSubmit,
                        onRegisterRouteStartClick = onRegisterRouteStartClick,
                        onRegisterRouteNameChange = onRegisterRouteNameChange,
                        onRegisterRouteEmojiSelect = onRegisterRouteEmojiSelect,
                        onRegisterRouteCancel = onRegisterRouteCancel,
                        onRegisterRouteConfirm = onRegisterRouteConfirm,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    DallimPrimaryButton(text = "공유하기", onClick = onShareClick, modifier = Modifier.weight(1f))
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DallimTextButton(text = "달림북에 저장하고 닫기", onClick = onDoneClick)
                }
                Box(modifier = Modifier.padding(bottom = Spacing.lg))
            }
        }
    }
}

/**
 * [run.routeId]가 null이면 자유 러닝(2026-09-26, 사용자 요청) 결과다 — Match/커버리지 %와
 * "이 코스 어땠나요" 코스 평가는 목표 코스가 있어야 의미가 있는 지표라 둘 다 숨기고, 대신
 * "이 경로를 코스로 등록" 섹션을 보여준다.
 */
@Composable
private fun ResultContent(
    run: RunDetailResponseBody,
    selectedFeedbackTags: Set<String>,
    feedbackTagsSubmitted: Boolean,
    registerRouteState: RegisterRouteUiState,
    onFeedbackTagToggle: (String) -> Unit,
    onFeedbackTagsSubmit: () -> Unit,
    onRegisterRouteStartClick: () -> Unit,
    onRegisterRouteNameChange: (String) -> Unit,
    onRegisterRouteEmojiSelect: (String) -> Unit,
    onRegisterRouteCancel: () -> Unit,
    onRegisterRouteConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFreeform = run.routeId == null
    Column(modifier = modifier.padding(top = Spacing.xl)) {
        RunResultCanvas(
            coordinates = run.actualGeoJson.toGeoPoints(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl),
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl, start = Spacing.ScreenHorizontal, end = Spacing.ScreenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${RunFormat.km(run.distanceKm)}km",
                style = DallimTypography.Display,
                color = DallimColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (isFreeform) "자유 러닝 완료" else "${run.routeName} 그리기 완료",
                style = DallimTypography.Body,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MetricColumn(label = "시간", value = RunFormat.duration(run.durationSeconds.toLong()))
                MetricColumn(label = "페이스", value = "${RunFormat.pace(run.averagePaceSecPerKm)}/km")
            }

            Row(
                modifier = Modifier.padding(top = Spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                RunStatusBadge(status = run.status.toRunStatus())
                if (!isFreeform) {
                    Text(
                        text = "Match ${run.sketchMatchPercent}% · 커버리지 ${run.routeCompletionPercent}%",
                        style = DallimTypography.Caption,
                        color = DallimColors.TextSecondary,
                    )
                }
            }
        }

        if (isFreeform) {
            RegisterRouteSection(
                state = registerRouteState,
                onStartClick = onRegisterRouteStartClick,
                onNameChange = onRegisterRouteNameChange,
                onEmojiSelect = onRegisterRouteEmojiSelect,
                onCancel = onRegisterRouteCancel,
                onConfirm = onRegisterRouteConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xl, start = Spacing.ScreenHorizontal, end = Spacing.ScreenHorizontal),
            )
        } else {
            FeedbackTagsSection(
                selectedTags = selectedFeedbackTags,
                submitted = feedbackTagsSubmitted,
                onTagToggle = onFeedbackTagToggle,
                onSubmit = onFeedbackTagsSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xl, start = Spacing.ScreenHorizontal, end = Spacing.ScreenHorizontal),
            )
        }
    }
}

/**
 * 2026-09-26, 사용자 요청 — 자유 러닝을 완주한 뒤 그 궤적을 새 코스로 공개 등록하는 섹션.
 * 필터 없이 즉시 공개된다(UGC 모더레이션은 이번 라운드에 없음, CLAUDE.md 2026-09-26 결정).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegisterRouteSection(
    state: RegisterRouteUiState,
    onStartClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onEmojiSelect: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeader(title = "이 경로, 코스로 남길까요?")
        when (state) {
            is RegisterRouteUiState.Hidden -> DallimCard(onClick = onStartClick) {
                Text(text = "이 경로를 코스로 등록", style = DallimTypography.Body, color = DallimColors.TextPrimary)
                Text(
                    text = "이름을 붙이면 다른 사람도 이 그림을 달려볼 수 있어요.",
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            is RegisterRouteUiState.Editing -> DallimCard {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    placeholder = { Text("코스 이름 (예: 저녁 산책길)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(top = Spacing.sm),
                ) {
                    ROUTE_EMOJI_OPTIONS.forEach { emoji ->
                        EmojiChip(emoji = emoji, selected = emoji == state.emoji, onClick = { onEmojiSelect(emoji) })
                    }
                }
                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        style = DallimTypography.Caption,
                        color = DallimColors.Error,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    DallimSecondaryButton(text = "취소", onClick = onCancel, modifier = Modifier.weight(1f))
                    DallimPrimaryButton(
                        text = if (state.isSubmitting) "등록 중..." else "등록하기",
                        onClick = onConfirm,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            is RegisterRouteUiState.Registered -> DallimCard {
                Text(text = "코스로 등록됐어요 🎉", style = DallimTypography.Body, color = DallimColors.TextPrimary)
                Text(
                    text = "탐색에서 다른 사람들도 이 코스를 찾을 수 있어요.",
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun EmojiChip(emoji: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) DallimColors.PrimaryLight else DallimColors.Background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, style = DallimTypography.Title2)
    }
}

/**
 * S-25 "코스 평가 요청(태그 선택 3초 컷)" (v1.3 문서 395행, 483~484행 원칙 — 별점 없는 긍정 행동
 * 태그만, 최대 3개, 미선택도 완전히 허용). 실패해도 결과 확인 자체엔 지장이 없어야 하므로 제출
 * 실패는 조용히 무시한다(ViewModel 쪽 처리) — 이 섹션엔 별도 에러 UI가 없다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackTagsSection(
    selectedTags: Set<String>,
    submitted: Boolean,
    onTagToggle: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeader(title = "이 코스 어땠나요?")
        if (submitted) {
            Text(
                text = "평가해주셔서 감사해요",
                style = DallimTypography.Body,
                color = DallimColors.TextSecondary,
            )
            return@Column
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FEEDBACK_TAG_OPTIONS.forEach { tag ->
                DallimFilterChip(
                    label = tag,
                    selected = tag in selectedTags,
                    onClick = { onTagToggle(tag) },
                )
            }
        }

        DallimSecondaryButton(
            text = "평가 제출",
            onClick = onSubmit,
            enabled = selectedTags.isNotEmpty(),
            modifier = Modifier.padding(top = Spacing.md),
        )
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = DallimTypography.Title1, color = DallimColors.TextPrimary)
        Text(
            text = label,
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

private fun GeoJsonLineString.toGeoPoints(): List<GeoPoint> =
    toLngLatPairs().map { (lng, lat) -> GeoPoint(lng = lng, lat = lat) }

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun RunResultScreenPreview() {
    DallimTheme {
        RunResultScreen(
            uiState = RunResultUiState.Success(
                run = RunDetailResponseBody(
                    runId = "run_301",
                    routeId = "rt_001",
                    routeName = "고래",
                    status = "COMPLETED",
                    actualGeoJson = GeoJsonLineString(
                        coordinates = listOf(
                            listOf(127.05, 37.25),
                            listOf(127.052, 37.253),
                            listOf(127.055, 37.251),
                            listOf(127.058, 37.256),
                        ),
                    ),
                    plannedGeoJson = GeoJsonLineString(),
                    distanceKm = 5.18,
                    durationSeconds = 2078,
                    averagePaceSecPerKm = 401,
                    sketchMatchPercent = 92,
                    routeCompletionPercent = 97,
                    completedAt = "2026-08-23T09:34:38Z",
                ),
            ),
            selectedFeedbackTags = emptySet(),
            feedbackTagsSubmitted = false,
            registerRouteState = RegisterRouteUiState.Hidden,
            onFeedbackTagToggle = {},
            onFeedbackTagsSubmit = {},
            onRegisterRouteStartClick = {},
            onRegisterRouteNameChange = {},
            onRegisterRouteEmojiSelect = {},
            onRegisterRouteCancel = {},
            onRegisterRouteConfirm = {},
            onShareClick = {},
            onDoneClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun RunResultScreenFreeformPreview() {
    DallimTheme {
        RunResultScreen(
            uiState = RunResultUiState.Success(
                run = RunDetailResponseBody(
                    runId = "run_302",
                    routeId = null,
                    routeName = null,
                    status = "COMPLETED",
                    actualGeoJson = GeoJsonLineString(
                        coordinates = listOf(
                            listOf(127.05, 37.25),
                            listOf(127.052, 37.253),
                            listOf(127.055, 37.251),
                        ),
                    ),
                    plannedGeoJson = null,
                    distanceKm = 3.2,
                    durationSeconds = 1200,
                    averagePaceSecPerKm = 375,
                    sketchMatchPercent = 0,
                    routeCompletionPercent = 0,
                    completedAt = "2026-09-26T09:34:38Z",
                ),
            ),
            selectedFeedbackTags = emptySet(),
            feedbackTagsSubmitted = false,
            registerRouteState = RegisterRouteUiState.Editing(name = "저녁 산책길"),
            onFeedbackTagToggle = {},
            onFeedbackTagsSubmit = {},
            onRegisterRouteStartClick = {},
            onRegisterRouteNameChange = {},
            onRegisterRouteEmojiSelect = {},
            onRegisterRouteCancel = {},
            onRegisterRouteConfirm = {},
            onShareClick = {},
            onDoneClick = {},
            onRetryClick = {},
        )
    }
}
