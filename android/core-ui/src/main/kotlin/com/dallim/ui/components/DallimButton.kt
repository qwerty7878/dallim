package com.dallim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes

/**
 * Primary action button — docs/04-ui-guide.md §6. One of these per screen at most (§2 위계).
 * 56dp tall to satisfy the minimum tap target (docs/03-design-system.md §3.3).
 */
@Composable
fun DallimPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DallimShapes.MinTapTarget)
            .clip(DallimShapes.ButtonCorner)
            .background(if (enabled) DallimColors.Primary else DallimColors.Primary.copy(alpha = 0.3f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Secondary (outline) button — used when a screen needs a second, clearly de-emphasized action. */
@Composable
fun DallimSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DallimShapes.MinTapTarget)
            .clip(DallimShapes.ButtonCorner)
            .background(Color.Transparent)
            .border(1.dp, if (enabled) DallimColors.Primary else DallimColors.Border, DallimShapes.ButtonCorner)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = if (enabled) DallimColors.Primary else DallimColors.TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Plain text button — the lowest-emphasis action (e.g. S-02 "이메일로 시작하기", "나중에"). */
@Composable
fun DallimTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(DallimShapes.MinTapTarget)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = if (enabled) DallimColors.TextSecondary else DallimColors.TextSecondary.copy(alpha = 0.4f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
