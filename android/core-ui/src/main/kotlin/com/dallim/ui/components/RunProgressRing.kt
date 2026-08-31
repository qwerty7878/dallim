package com.dallim.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimGradient
import com.dallim.ui.theme.DallimTypography

/**
 * S-21 진행률 링 — 계획 경로 커버리지(로컬 프리체크 %)를 원형 게이지로 보여준다
 * (docs/04-ui-guide.md §5 화면 골격 "◯ 62% 그림 완성도"). 다크 고정 화면(§9)에서 쓰이므로
 * 트랙은 옅은 흰색, 진행 아크는 시그니처 그라디언트를 쓴다.
 */
@Composable
fun RunProgressRing(
    progressPercent: Int,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 10.dp,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2
            val arcSize = Size(this.size.width - strokeWidth.toPx(), this.size.height - strokeWidth.toPx())
            drawArc(
                color = DallimColors.Surface.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            drawArc(
                brush = DallimGradient,
                startAngle = -90f,
                sweepAngle = 360f * (progressPercent.coerceIn(0, 100) / 100f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
        Text(
            text = "${progressPercent.coerceIn(0, 100)}%",
            style = DallimTypography.Title1,
            color = DallimColors.Surface,
            textAlign = TextAlign.Center,
        )
    }
}
