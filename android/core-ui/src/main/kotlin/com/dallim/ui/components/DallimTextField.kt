package com.dallim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * Form input (2026-10-04 재작성). Material `OutlinedTextField`의 떠다니는 라벨/외곽선은 "기본 머티리얼"
 * 인상의 대표 요소라, 라벨을 위에 고정하고 옅은 회색 면 위에 입력하는 형태로 바꿨다. 포커스는 Primary
 * 외곽선 1.5dp, 오류는 Error 외곽선 + 하단 캡션. 단일 Primary 색만 사용한다(폼에 그라디언트 금지).
 */
@Composable
fun DallimTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    errorText: String? = null,
    singleLine: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    val borderColor = when {
        errorText != null -> DallimColors.Error
        focused -> DallimColors.Primary
        else -> androidx.compose.ui.graphics.Color.Transparent
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            interactionSource = interaction,
            textStyle = DallimTypography.Body.copy(color = DallimColors.TextPrimary),
            cursorBrush = SolidColor(DallimColors.Primary),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (singleLine) Modifier.height(52.dp) else Modifier)
                        .clip(shape)
                        .background(DallimColors.SurfaceMuted)
                        .border(1.5.dp, borderColor, shape)
                        .padding(horizontal = Spacing.md, vertical = 14.dp),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(text = placeholder, style = DallimTypography.Body, color = DallimColors.TextTertiary)
                    }
                    inner()
                }
            },
        )
        if (errorText != null) {
            Text(
                text = errorText,
                style = DallimTypography.Caption,
                color = DallimColors.Error,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}
