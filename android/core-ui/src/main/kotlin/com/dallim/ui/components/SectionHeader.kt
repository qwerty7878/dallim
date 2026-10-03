package com.dallim.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/** 섹션 제목 — Title3(17sp SemiBold). 섹션 간격은 호출부가 [Spacing.xl]로 준다. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = DallimTypography.Title3,
        color = DallimColors.TextPrimary,
        modifier = modifier.padding(bottom = Spacing.md),
    )
}
