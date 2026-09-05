package com.dallim.app.career

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.dallim.ui.components.DallimFilterChip
import com.dallim.ui.components.DallimTextField
import com.dallim.ui.components.SectionHeader
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/** 자유 입력값 중 숫자만 남기고, 자릿수를 넘거나 숫자가 아니면 null(호출부는 무시하고 갱신하지 않음). */
fun sanitizedDigitsOrNull(value: String, maxLength: Int): String? =
    value.takeIf { it.isEmpty() || (it.length <= maxLength && it.all(Char::isDigit)) }

/**
 * S-04b/S-90 공용 입력 폼 — docs/02-api-spec.md 15.3 필드 그대로: 대회명 / 종목(칩, OTHER면
 * 거리 입력 추가) / 연도 / 기록(hh:mm:ss, 선택). [showExtendedFields]가 true면 S-90 전용 필드
 * (기록종류/배번호/메모)도 함께 그린다 — S-04b(온보딩)는 간이 입력이라 false로 호출한다.
 *
 * 호출부(Column + verticalScroll)에 얹히는 형태라 자체 스크롤은 갖지 않는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RaceRecordFormFields(
    state: RaceRecordFormState,
    onRaceNameChange: (String) -> Unit,
    onCategorySelected: (RaceCategoryOption) -> Unit,
    onOtherDistanceChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onSecondsChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    showExtendedFields: Boolean = false,
    onRecordTypeSelected: (RecordTypeOption?) -> Unit = {},
    onBibNumberChange: (String) -> Unit = {},
    onMemoChange: (String) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "대회명")
        DallimTextField(
            value = state.raceName,
            onValueChange = onRaceNameChange,
            label = "대회명",
            placeholder = "예: 2026 서울 하프마라톤",
        )

        SectionHeader(title = "종목", modifier = Modifier.padding(top = Spacing.xl))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RaceCategoryOption.entries.forEach { option ->
                DallimFilterChip(
                    label = option.label,
                    selected = state.category == option,
                    onClick = { onCategorySelected(option) },
                )
            }
        }
        if (state.category?.requiresDistanceInput == true) {
            DallimTextField(
                value = state.otherDistanceKmInput,
                onValueChange = onOtherDistanceChange,
                label = "거리 (km)",
                placeholder = "예: 15",
                keyboardType = KeyboardType.Decimal,
                errorText = state.distanceKmError,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        SectionHeader(title = "연도", modifier = Modifier.padding(top = Spacing.xl))
        DallimTextField(
            value = state.yearInput,
            onValueChange = onYearChange,
            label = "연도",
            placeholder = "예: 2026",
            keyboardType = KeyboardType.Number,
            errorText = state.yearError,
        )

        SectionHeader(title = "기록 (선택)", modifier = Modifier.padding(top = Spacing.xl))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            DallimTextField(
                value = state.hoursInput,
                onValueChange = { sanitizedDigitsOrNull(it, 2)?.let(onHoursChange) },
                label = "시간",
                placeholder = "0",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            DallimTextField(
                value = state.minutesInput,
                onValueChange = { sanitizedDigitsOrNull(it, 2)?.let(onMinutesChange) },
                label = "분",
                placeholder = "0",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            DallimTextField(
                value = state.secondsInput,
                onValueChange = { sanitizedDigitsOrNull(it, 2)?.let(onSecondsChange) },
                label = "초",
                placeholder = "0",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        val recordTimeError = state.recordTimeError
        if (recordTimeError != null) {
            Text(
                text = recordTimeError,
                style = DallimTypography.Caption,
                color = DallimColors.Error,
                modifier = Modifier.padding(top = Spacing.xs, start = Spacing.xs),
            )
        }

        if (showExtendedFields) {
            SectionHeader(title = "기록 종류 (선택)", modifier = Modifier.padding(top = Spacing.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RecordTypeOption.entries.forEach { option ->
                    DallimFilterChip(
                        label = option.label,
                        selected = state.recordType == option,
                        onClick = { onRecordTypeSelected(if (state.recordType == option) null else option) },
                    )
                }
            }

            SectionHeader(title = "배번호 (선택)", modifier = Modifier.padding(top = Spacing.xl))
            DallimTextField(
                value = state.bibNumber,
                onValueChange = onBibNumberChange,
                label = "배번호",
                placeholder = "예: A1234",
            )

            SectionHeader(title = "메모 (선택)", modifier = Modifier.padding(top = Spacing.xl))
            DallimTextField(
                value = state.memo,
                onValueChange = onMemoChange,
                label = "메모",
                placeholder = "첫 하프, 페이스 메이커 등 남기고 싶은 말",
                singleLine = false,
            )
        }
    }
}
