package com.dallim.app.career.edit

import com.dallim.ui.icons.DallimIcons
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.dallim.app.career.RecordTypeOption
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-90 완주 이력 등록/편집 — docs/02-api-spec.md 15.3~15.5. 등록/수정 모두 같은 폼
 * ([RaceRecordFormFields], S-04b와 공유)을 쓰고, 수정 모드에서만 삭제 버튼을 더 보여준다.
 * 자기신고 이력이므로 저장 버튼 위에 "미인증" 안내 캡션을 항상 둔다.
 */
@Composable
fun RaceRecordEditRoute(
    onBackClick: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RaceRecordEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                RaceRecordEditNavigationEvent.Saved -> onSaved()
                RaceRecordEditNavigationEvent.Deleted -> onDeleted()
            }
        }
    }

    RaceRecordEditScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetryClick = viewModel::retryLoad,
        onRaceNameChange = viewModel::onRaceNameChange,
        onCategorySelected = viewModel::onCategorySelected,
        onOtherDistanceChange = viewModel::onOtherDistanceChange,
        onYearChange = viewModel::onYearChange,
        onHoursChange = viewModel::onHoursChange,
        onMinutesChange = viewModel::onMinutesChange,
        onSecondsChange = viewModel::onSecondsChange,
        onRecordTypeSelected = viewModel::onRecordTypeSelected,
        onBibNumberChange = viewModel::onBibNumberChange,
        onMemoChange = viewModel::onMemoChange,
        onSubmit = viewModel::onSubmit,
        onDeleteClick = viewModel::onDeleteClick,
        onDismissDeleteConfirm = viewModel::onDismissDeleteConfirm,
        onConfirmDelete = viewModel::onConfirmDelete,
        modifier = modifier,
    )
}

@Composable
private fun RaceRecordEditScreen(
    uiState: RaceRecordEditUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onRaceNameChange: (String) -> Unit,
    onCategorySelected: (RaceCategoryOption) -> Unit,
    onOtherDistanceChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onSecondsChange: (String) -> Unit,
    onRecordTypeSelected: (RecordTypeOption?) -> Unit,
    onBibNumberChange: (String) -> Unit,
    onMemoChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDeleteClick: () -> Unit,
    onDismissDeleteConfirm: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val isEditMode = (uiState as? RaceRecordEditUiState.Ready)?.isEditMode ?: (uiState is RaceRecordEditUiState.Loading)
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
                text = if (isEditMode) "완주 이력 수정" else "완주 이력 등록",
                style = DallimTypography.Title1,
                color = DallimColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (uiState is RaceRecordEditUiState.Ready && uiState.isEditMode) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = DallimIcons.Trash2,
                        contentDescription = "삭제",
                        tint = DallimColors.Error,
                    )
                }
            }
        }

        when (uiState) {
            is RaceRecordEditUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
            is RaceRecordEditUiState.LoadError -> DallimErrorState(
                title = "완주 이력을 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            is RaceRecordEditUiState.Ready -> Column(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.ScreenHorizontal),
                ) {
                    RaceRecordFormFields(
                        state = uiState.form,
                        onRaceNameChange = onRaceNameChange,
                        onCategorySelected = onCategorySelected,
                        onOtherDistanceChange = onOtherDistanceChange,
                        onYearChange = onYearChange,
                        onHoursChange = onHoursChange,
                        onMinutesChange = onMinutesChange,
                        onSecondsChange = onSecondsChange,
                        showExtendedFields = true,
                        onRecordTypeSelected = onRecordTypeSelected,
                        onBibNumberChange = onBibNumberChange,
                        onMemoChange = onMemoChange,
                        modifier = Modifier.padding(top = Spacing.md),
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
                    // 자기신고 이력이라는 사실을 저장 버튼 바로 위에 둬서, 저장을 누르기 직전에
                    // 항상 보이게 한다("인증됨"으로 오해할 수 있는 표현 금지 — CLAUDE.md 규칙).
                    Text(
                        text = "직접 입력한 기록이에요. 별도의 인증 절차는 없어요.",
                        style = DallimTypography.Caption,
                        color = DallimColors.TextSecondary,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                    DallimPrimaryButton(
                        text = if (uiState.isSubmitting) "저장하는 중..." else "저장",
                        onClick = onSubmit,
                        enabled = uiState.form.isValid && !uiState.isSubmitting && !uiState.isDeleting,
                    )
                }

                if (uiState.showDeleteConfirm) {
                    DeleteConfirmDialog(onDismiss = onDismissDeleteConfirm, onConfirm = onConfirmDelete)
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = DallimShapes.CardCorner, color = DallimColors.Surface) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(text = "완주 이력을 삭제할까요?", style = DallimTypography.Title2, color = DallimColors.TextPrimary)
                Text(
                    text = "삭제하면 되돌릴 수 없어요.",
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.lg),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DallimTextButton(text = "취소", onClick = onDismiss)
                    Box(modifier = Modifier.padding(start = Spacing.sm)) {
                        DallimTextButton(text = "삭제", onClick = onConfirm)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun RaceRecordEditCreatePreview() {
    DallimTheme {
        RaceRecordEditScreen(
            uiState = RaceRecordEditUiState.Ready(isEditMode = false),
            onBackClick = {},
            onRetryClick = {},
            onRaceNameChange = {},
            onCategorySelected = {},
            onOtherDistanceChange = {},
            onYearChange = {},
            onHoursChange = {},
            onMinutesChange = {},
            onSecondsChange = {},
            onRecordTypeSelected = {},
            onBibNumberChange = {},
            onMemoChange = {},
            onSubmit = {},
            onDeleteClick = {},
            onDismissDeleteConfirm = {},
            onConfirmDelete = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun RaceRecordEditEditPreview() {
    DallimTheme {
        RaceRecordEditScreen(
            uiState = RaceRecordEditUiState.Ready(
                isEditMode = true,
                form = RaceRecordFormState(
                    raceName = "2026 서울 하프마라톤",
                    category = RaceCategoryOption.HALF,
                    hoursInput = "1",
                    minutesInput = "45",
                    secondsInput = "0",
                    recordType = RecordTypeOption.NET,
                    bibNumber = "A1234",
                ),
            ),
            onBackClick = {},
            onRetryClick = {},
            onRaceNameChange = {},
            onCategorySelected = {},
            onOtherDistanceChange = {},
            onYearChange = {},
            onHoursChange = {},
            onMinutesChange = {},
            onSecondsChange = {},
            onRecordTypeSelected = {},
            onBibNumberChange = {},
            onMemoChange = {},
            onSubmit = {},
            onDeleteClick = {},
            onDismissDeleteConfirm = {},
            onConfirmDelete = {},
        )
    }
}
