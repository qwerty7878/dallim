package com.dallim.app.onboarding.career

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.career.RaceCategoryOption
import com.dallim.app.career.RaceRecordFormFields
import com.dallim.app.career.RaceRecordFormState
import com.dallim.app.career.RaceRecordFormat
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimSecondaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-04b 러닝 커리어 입력 — docs/달림_화면별_상세기획서_v1.3.md PART 3-A. [있어요]/[아직 없어요]
 * 질문에서 시작해, [있어요]를 고르면 같은 화면 안에서 간이 폼으로 전환된다. [건너뛰기]는 단계와
 * 무관하게 상단에 항상 떠 있다.
 */
@Composable
fun CareerEntryRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CareerEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                CareerEntryNavigationEvent.Finished -> onFinished()
            }
        }
    }

    CareerEntryScreen(
        uiState = uiState,
        currentComfortablePaceApiValue = viewModel.currentComfortablePaceApiValue,
        onSkip = viewModel::onSkip,
        onHasExperienceYes = viewModel::onHasExperienceYes,
        onHasExperienceNo = viewModel::onHasExperienceNo,
        onRaceNameChange = viewModel::onRaceNameChange,
        onCategorySelected = viewModel::onCategorySelected,
        onOtherDistanceChange = viewModel::onOtherDistanceChange,
        onYearChange = viewModel::onYearChange,
        onHoursChange = viewModel::onHoursChange,
        onMinutesChange = viewModel::onMinutesChange,
        onSecondsChange = viewModel::onSecondsChange,
        onSubmit = viewModel::onSubmit,
        onAcknowledgePaceSuggestion = viewModel::onAcknowledgePaceSuggestion,
        modifier = modifier,
    )
}

@Composable
private fun CareerEntryScreen(
    uiState: CareerEntryUiState,
    currentComfortablePaceApiValue: String,
    onSkip: () -> Unit,
    onHasExperienceYes: () -> Unit,
    onHasExperienceNo: () -> Unit,
    onRaceNameChange: (String) -> Unit,
    onCategorySelected: (RaceCategoryOption) -> Unit,
    onOtherDistanceChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onSecondsChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onAcknowledgePaceSuggestion: () -> Unit,
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
            horizontalArrangement = Arrangement.End,
        ) {
            DallimTextButton(text = "건너뛰기", onClick = onSkip)
        }

        when (uiState.step) {
            CareerEntryStep.QUESTION -> QuestionStep(
                onYes = onHasExperienceYes,
                onNo = onHasExperienceNo,
                modifier = Modifier.weight(1f),
            )

            CareerEntryStep.FORM -> FormStep(
                uiState = uiState,
                onRaceNameChange = onRaceNameChange,
                onCategorySelected = onCategorySelected,
                onOtherDistanceChange = onOtherDistanceChange,
                onYearChange = onYearChange,
                onHoursChange = onHoursChange,
                onMinutesChange = onMinutesChange,
                onSecondsChange = onSecondsChange,
                onSubmit = onSubmit,
                modifier = Modifier.weight(1f),
            )
        }
    }

    val suggestion = uiState.pendingPaceSuggestion
    if (suggestion != null) {
        PaceSuggestionDialog(
            suggestedPaceApiValue = suggestion,
            currentPaceApiValue = currentComfortablePaceApiValue,
            onAcknowledge = onAcknowledgePaceSuggestion,
        )
    }
}

/** 온보딩 전용 가운데 정렬 — docs/04-ui-guide.md §5 예외 3곳 중 "온보딩". */
@Composable
private fun QuestionStep(
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ScreenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "대회에 참가해보신 적 있나요?",
            style = DallimTypography.Title1,
            color = DallimColors.TextPrimary,
        )
        Text(
            text = "나중에 마이 탭에서 언제든 더 추가할 수 있어요.",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xl),
        )
        DallimPrimaryButton(text = "있어요", onClick = onYes)
        DallimSecondaryButton(text = "아직 없어요", onClick = onNo, modifier = Modifier.padding(top = Spacing.sm))
    }
}

@Composable
private fun FormStep(
    uiState: CareerEntryUiState,
    onRaceNameChange: (String) -> Unit,
    onCategorySelected: (RaceCategoryOption) -> Unit,
    onOtherDistanceChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onSecondsChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.ScreenHorizontal),
        ) {
            Text(
                text = "완주 이력을 알려주세요",
                style = DallimTypography.Title1,
                color = DallimColors.TextPrimary,
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xl),
            )

            RaceRecordFormFields(
                state = uiState.form,
                onRaceNameChange = onRaceNameChange,
                onCategorySelected = onCategorySelected,
                onOtherDistanceChange = onOtherDistanceChange,
                onYearChange = onYearChange,
                onHoursChange = onHoursChange,
                onMinutesChange = onMinutesChange,
                onSecondsChange = onSecondsChange,
                showExtendedFields = false,
            )

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    style = DallimTypography.Caption,
                    color = DallimColors.Error,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md)) {
            DallimPrimaryButton(
                text = if (uiState.isSubmitting) "저장하는 중..." else "완료",
                onClick = onSubmit,
                enabled = uiState.form.isValid && !uiState.isSubmitting,
            )
        }
    }
}

/**
 * 하프/풀 기록 기준 예상 페이스가 S-04에서 고른 값과 다를 때만 뜨는 안내. **프로필 페이스를
 * 실제로 갱신하는 API가 없어**(CareerEntryViewModel 문서 참고) [확인] 한 번만 있는 정보성
 * 배너 형태로 두고, 실제로 반영된 것처럼 보이는 "동의/반영" 문구는 쓰지 않는다.
 */
@Composable
private fun PaceSuggestionDialog(
    suggestedPaceApiValue: String,
    currentPaceApiValue: String,
    onAcknowledge: () -> Unit,
) {
    Dialog(onDismissRequest = onAcknowledge) {
        Surface(shape = DallimShapes.CardCorner, color = DallimColors.Surface) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(text = "예상 편안한 페이스", style = DallimTypography.Title2, color = DallimColors.TextPrimary)
                Text(
                    text = "입력하신 기록 기준으로 예상한 편안한 페이스는 " +
                        "${RaceRecordFormat.paceLabel(suggestedPaceApiValue)}이에요. " +
                        "지금 설정하신 페이스(${RaceRecordFormat.paceLabel(currentPaceApiValue)})와 달라요.",
                    style = DallimTypography.Body,
                    color = DallimColors.TextPrimary,
                    modifier = Modifier.padding(top = Spacing.md),
                )
                Text(
                    text = "프로필 페이스를 다시 수정하는 기능은 아직 없어서, 이번엔 참고만 해주세요.",
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                DallimPrimaryButton(text = "확인", onClick = onAcknowledge, modifier = Modifier.padding(top = Spacing.lg))
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun CareerEntryQuestionPreview() {
    DallimTheme {
        CareerEntryScreen(
            uiState = CareerEntryUiState(step = CareerEntryStep.QUESTION),
            currentComfortablePaceApiValue = "PACE_6_7",
            onSkip = {},
            onHasExperienceYes = {},
            onHasExperienceNo = {},
            onRaceNameChange = {},
            onCategorySelected = {},
            onOtherDistanceChange = {},
            onYearChange = {},
            onHoursChange = {},
            onMinutesChange = {},
            onSecondsChange = {},
            onSubmit = {},
            onAcknowledgePaceSuggestion = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun CareerEntryFormPreview() {
    DallimTheme {
        CareerEntryScreen(
            uiState = CareerEntryUiState(
                step = CareerEntryStep.FORM,
                form = RaceRecordFormState(
                    raceName = "2026 서울 하프마라톤",
                    category = RaceCategoryOption.HALF,
                    hoursInput = "1",
                    minutesInput = "45",
                    secondsInput = "0",
                ),
            ),
            currentComfortablePaceApiValue = "PACE_6_7",
            onSkip = {},
            onHasExperienceYes = {},
            onHasExperienceNo = {},
            onRaceNameChange = {},
            onCategorySelected = {},
            onOtherDistanceChange = {},
            onYearChange = {},
            onHoursChange = {},
            onMinutesChange = {},
            onSecondsChange = {},
            onSubmit = {},
            onAcknowledgePaceSuggestion = {},
        )
    }
}
