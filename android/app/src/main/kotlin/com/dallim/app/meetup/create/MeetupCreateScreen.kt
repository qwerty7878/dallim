package com.dallim.app.meetup.create

import com.dallim.ui.icons.DallimIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimTopBar
import com.dallim.ui.components.DallimTextField
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateDisplayFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
private val TimeDisplayFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

/**
 * S-48 모집 만들기 — 날짜·시간, 정원(2~20명), 설명(선택) 입력 (docs/01-feature-spec.md §1.8,
 * docs/02-api-spec.md 14.3). 성공 시 [onCreated]를 호출해 NavHost가 S-47로 돌아가면서 목록을
 * 새로고침하도록 위임한다.
 */
@Composable
fun MeetupCreateRoute(
    onBackClick: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeetupCreateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                MeetupCreateNavigationEvent.Created -> onCreated()
            }
        }
    }

    MeetupCreateScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onDateSelected = viewModel::onDateSelected,
        onTimeSelected = viewModel::onTimeSelected,
        onMaxParticipantsChange = viewModel::onMaxParticipantsChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onSubmit = viewModel::onSubmit,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeetupCreateScreen(
    uiState: MeetupCreateUiState,
    onBackClick: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onTimeSelected: (LocalTime) -> Unit,
    onMaxParticipantsChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        DallimTopBar(title = "모집 만들기", onBackClick = onBackClick)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.ScreenHorizontal),
        ) {
            SectionLabel(text = "날짜·시간")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                MeetupPickerField(
                    icon = DallimIcons.Calendar,
                    displayText = uiState.date?.format(DateDisplayFormatter),
                    placeholder = "날짜 선택",
                    isError = uiState.dateTimeError != null,
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1.2f),
                )
                MeetupPickerField(
                    icon = DallimIcons.Clock,
                    displayText = uiState.time?.format(TimeDisplayFormatter),
                    placeholder = "시간 선택",
                    isError = uiState.dateTimeError != null,
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                )
            }
            val dateTimeError = uiState.dateTimeError
            if (dateTimeError != null) {
                Text(
                    text = dateTimeError,
                    style = DallimTypography.Caption,
                    color = DallimColors.Error,
                    modifier = Modifier.padding(top = Spacing.xs, start = Spacing.xs),
                )
            }

            Box(modifier = Modifier.padding(top = Spacing.lg)) {
                SectionLabel(text = "정원")
            }
            DallimTextField(
                value = uiState.maxParticipantsInput,
                onValueChange = onMaxParticipantsChange,
                label = "최대 인원",
                placeholder = "2~20명",
                keyboardType = KeyboardType.Number,
                errorText = uiState.maxParticipantsError,
            )

            Box(modifier = Modifier.padding(top = Spacing.lg)) {
                SectionLabel(text = "설명 (선택)")
            }
            DallimTextField(
                value = uiState.description,
                onValueChange = onDescriptionChange,
                label = "설명",
                placeholder = "페이스, 준비물 등 같이 뛸 사람에게 전할 말을 적어주세요.",
                singleLine = false,
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

        Box(modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md)) {
            DallimPrimaryButton(
                text = if (uiState.isSubmitting) "만드는 중..." else "모집 만들기",
                onClick = onSubmit,
                enabled = uiState.canSubmit,
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.date
                ?.atStartOfDay(ZoneId.of("UTC"))
                ?.toInstant()
                ?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate())
                    }
                    showDatePicker = false
                }) {
                    Text("확인", color = DallimColors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("취소", color = DallimColors.TextSecondary)
                }
            },
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = DallimColors.Primary,
                    todayDateBorderColor = DallimColors.Primary,
                    todayContentColor = DallimColors.Primary,
                ),
            )
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = uiState.time?.hour ?: LocalTime.now().hour,
            initialMinute = uiState.time?.minute ?: 0,
            is24Hour = false,
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = DallimShapes.CardCorner, color = DallimColors.Surface) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = "시간 선택", style = DallimTypography.Title2, color = DallimColors.TextPrimary)
                    Box(modifier = Modifier.padding(top = Spacing.md)) {
                        TimePicker(
                            state = timePickerState,
                            colors = TimePickerDefaults.colors(
                                selectorColor = DallimColors.Primary,
                                periodSelectorSelectedContainerColor = DallimColors.PrimaryLight,
                                periodSelectorSelectedContentColor = DallimColors.Primary,
                                timeSelectorSelectedContainerColor = DallimColors.PrimaryLight,
                                timeSelectorSelectedContentColor = DallimColors.Primary,
                            ),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("취소", color = DallimColors.TextSecondary)
                        }
                        TextButton(onClick = {
                            onTimeSelected(LocalTime.of(timePickerState.hour, timePickerState.minute))
                            showTimePicker = false
                        }) {
                            Text("확인", color = DallimColors.Primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = DallimTypography.Caption,
        color = DallimColors.TextSecondary,
        modifier = modifier.padding(bottom = Spacing.xs),
    )
}

@Composable
private fun MeetupPickerField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    displayText: String?,
    placeholder: String,
    isError: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(DallimShapes.MinTapTarget)
            .clip(DallimShapes.ButtonCorner)
            .border(1.dp, if (isError) DallimColors.Error else DallimColors.Border, DallimShapes.ButtonCorner)
            .clickable { onClick() }
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DallimColors.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Box(modifier = Modifier.width(Spacing.xs))
        Text(
            text = displayText ?: placeholder,
            style = DallimTypography.Body,
            color = if (displayText != null) DallimColors.TextPrimary else DallimColors.TextSecondary,
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun MeetupCreateScreenPreview() {
    DallimTheme {
        MeetupCreateScreen(
            uiState = MeetupCreateUiState(),
            onBackClick = {},
            onDateSelected = {},
            onTimeSelected = {},
            onMaxParticipantsChange = {},
            onDescriptionChange = {},
            onSubmit = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun MeetupCreateScreenErrorPreview() {
    DallimTheme {
        MeetupCreateScreen(
            uiState = MeetupCreateUiState(
                date = LocalDate.of(2026, 1, 1),
                time = LocalTime.of(9, 0),
                maxParticipantsInput = "30",
                errorMessage = "정원은 2~20명 사이로 정해주세요.",
            ),
            onBackClick = {},
            onDateSelected = {},
            onTimeSelected = {},
            onMaxParticipantsChange = {},
            onDescriptionChange = {},
            onSubmit = {},
        )
    }
}
