package com.dallim.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTypography

/** 읽기 전용 태그(러닝 스타일 등) — 면 채움 없이 얇은 테두리의 작은 직사각형. 선택용 칩은 [DallimFilterChip]. */
@Composable
fun DallimTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = DallimTypography.Label,
        color = DallimColors.TextSecondary,
        modifier = modifier
            .border(1.dp, DallimColors.Border, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
