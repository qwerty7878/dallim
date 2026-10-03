package com.dallim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.Spacing

/**
 * 카드 = 흰 배경 위의 옅은 회색 "면"(2026-10-04 개정). 이전의 [흰 박스 + 부드러운 그림자]는 모든 화면이
 * 같은 템플릿처럼 보이는 가장 큰 원인이었다 — 그림자도 테두리도 없이 면 색만으로 구분한다
 * (docs/04-ui-guide.md §4). 카드 안에 카드를 넣지 않는다.
 */
@Composable
fun DallimCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DallimShapes.CardCorner)
            .background(DallimColors.SurfaceMuted)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(Spacing.md),
        content = content,
    )
}
