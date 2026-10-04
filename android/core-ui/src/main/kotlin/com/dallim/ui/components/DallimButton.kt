package com.dallim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTypography

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
            .background(if (enabled) DallimColors.ActionFill else DallimColors.SurfaceMuted)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) DallimColors.OnActionFill else DallimColors.TextTertiary,
            style = DallimTypography.Title3,
        )
    }
}

/**
 * Secondary button — 외곽선 대신 옅은 회색 면(2026-10-04: 보라 외곽선 버튼은 Primary 버튼과 시선을
 * 다투고 템플릿처럼 보였다). 텍스트는 본문색.
 */
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
            .background(DallimColors.SurfaceMuted)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) DallimColors.TextPrimary else DallimColors.TextTertiary,
            style = DallimTypography.Title3,
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
        Text(
            text = text,
            color = if (enabled) DallimColors.TextSecondary else DallimColors.TextTertiary,
            style = DallimTypography.Body.copy(fontWeight = FontWeight.Medium),
        )
    }
}
