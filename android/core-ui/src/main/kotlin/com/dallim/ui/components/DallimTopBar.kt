package com.dallim.ui.components

import com.dallim.ui.icons.DallimIcons
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * 공용 상단바 (2026-10-04) — 화면마다 [뒤로가기 + 24sp 제목]을 손으로 짜서 간격/크기가 제각각이던 것을
 * 하나로 통일한다. 56dp 높이, 뒤로가기 + 17sp(Title3) 제목, 우측 액션 슬롯. 인셋(상태바)은 호출부가 처리한다.
 */
@Composable
fun DallimTopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = DallimIcons.ArrowLeft,
                contentDescription = "뒤로가기",
                tint = DallimColors.TextPrimary,
            )
        }
        Text(
            text = title,
            style = DallimTypography.Title3,
            color = DallimColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = Spacing.xs),
        )
        actions()
    }
}
