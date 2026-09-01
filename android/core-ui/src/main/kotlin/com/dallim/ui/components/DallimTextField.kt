package com.dallim.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * Form input — single Primary color only (no gradients on form elements, docs/03-design-system.md
 * §1.2 표), rounded to match the app's card/button corner language, with an optional inline
 * error caption underneath (S-02b validation messages).
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
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = singleLine,
            isError = errorText != null,
            shape = DallimShapes.ButtonCorner,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DallimColors.Primary,
                unfocusedBorderColor = DallimColors.Border,
                errorBorderColor = DallimColors.Error,
                cursorColor = DallimColors.Primary,
            ),
        )
        if (errorText != null) {
            Text(
                text = errorText,
                style = DallimTypography.Caption,
                color = DallimColors.Error,
                modifier = Modifier.padding(top = Spacing.xs, start = Spacing.xs),
            )
        }
    }
}
