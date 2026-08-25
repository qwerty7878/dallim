package com.dallim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * Single-select filter/option pill — selected = Primary fill, unselected = outline on Surface.
 * Never Material3's default `FilterChip` (docs/04-ui-guide.md §6 — custom components only).
 * Used for S-11 탐색 필터 rows; same visual language as the option chips already used in
 * S-04 프로필 설정 (kept private there, so this is the reusable core-ui version).
 */
@Composable
fun DallimFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) DallimColors.Primary else DallimColors.Surface)
            .border(
                width = 1.dp,
                color = if (selected) DallimColors.Primary else DallimColors.Border,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(
            text = label,
            style = DallimTypography.Caption,
            color = if (selected) Color.White else DallimColors.TextPrimary,
        )
    }
}
