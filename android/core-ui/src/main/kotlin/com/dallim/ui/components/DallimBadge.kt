package com.dallim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTypography

/**
 * 상태 표시 공통 컴포넌트 (2026-10-04 재설계) — 파스텔 배경 알약 대신 "색 점 + 글자"로 표시한다.
 * 각 *StatusBadge가 [foreground]로 상태 색을 주고, 글자는 항상 본문색 계열로 읽히게 둔다.
 * [background] 인자는 호환을 위해 남겨 두었으나 더 이상 칠하지 않는다.
 */
@Composable
fun DallimBadge(
    label: String,
    foreground: Color,
    @Suppress("UNUSED_PARAMETER") background: Color = Color.Transparent,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Spacer(modifier = Modifier.size(6.dp).clip(CircleShape).background(foreground))
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = label, style = DallimTypography.Label, color = DallimColors.TextSecondary)
    }
}
