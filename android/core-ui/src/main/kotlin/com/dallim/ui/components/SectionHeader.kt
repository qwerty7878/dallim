package com.dallim.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/** Section header (Title2, 20sp SemiBold) — docs/04-ui-guide.md §6. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = DallimTypography.Title2,
        color = DallimColors.TextPrimary,
        modifier = modifier.padding(bottom = Spacing.md),
    )
}
