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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTypography

private val ChipShape = RoundedCornerShape(8.dp)

/**
 * 선택 칩 (2026-10-04 재설계). 알약(pill) 모양 + 옅은 면 채움은 "생성형 UI" 인상의 대표 요소라, 작은 라운드
 * (8dp)의 직사각형 + 얇은 테두리로 바꿨다. 비선택 = 흰 바탕 + 1dp 테두리 + 보조색 글자, 선택 = Ink(거의 검정)
 * 채움 + 흰 글자. Material3 기본 `FilterChip`은 쓰지 않는다(docs/04-ui-guide.md §6).
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
            .clip(ChipShape)
            .background(if (selected) DallimColors.Ink else Color.Transparent)
            .border(1.dp, if (selected) DallimColors.Ink else DallimColors.Border, ChipShape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = DallimTypography.Body.copy(fontWeight = FontWeight.Medium),
            color = if (selected) Color.White else DallimColors.TextSecondary,
        )
    }
}
