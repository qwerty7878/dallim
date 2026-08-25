package com.dallim.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimGradient

/**
 * The app's brand mark, standalone (no gradient background square) — the same GPS-trail curve
 * used in `res/drawable/ic_launcher_foreground.xml`, redrawn on a transparent Canvas so it can
 * sit directly on any solid background (e.g. the splash screen, docs/04-ui-guide.md 스플래시
 * 결정: 배경 전체 그라디언트 대신 단색 배경 위에 아이콘 마크만 짧게 노출).
 *
 * Kept in sync by hand with the launcher icon's `pathData` — both draw the same normalized
 * 108x108 curve, scaled to whatever square [modifier] gives this Canvas.
 */
@Composable
fun DallimMark(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 10.dp,
) {
    Canvas(modifier = modifier) {
        val scaleX = size.width / 108f
        val scaleY = size.height / 108f
        fun pt(x: Float, y: Float) = Offset(x * scaleX, y * scaleY)

        val path = Path().apply {
            val start = pt(28f, 80f)
            moveTo(start.x, start.y)
            val c1 = pt(40f, 80f)
            val c2 = pt(38f, 54f)
            val mid = pt(54f, 54f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, mid.x, mid.y)
            val c3 = pt(70f, 54f)
            val c4 = pt(68f, 28f)
            val end = pt(80f, 28f)
            cubicTo(c3.x, c3.y, c4.x, c4.y, end.x, end.y)
        }

        drawPath(
            path = path,
            brush = DallimGradient,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
