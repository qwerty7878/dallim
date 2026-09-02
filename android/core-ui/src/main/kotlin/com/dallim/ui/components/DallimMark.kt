package com.dallim.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.dallim.ui.theme.DallimGradient

/**
 * The app's brand mark, standalone (no background square) — the same "러닝화 + 위치 핀" line-art
 * glyph used in `res/drawable/ic_launcher_foreground.xml` (2026-09-03 5차 수정: 흰색 채움 실루엣 ->
 * 그라디언트 라인아트), redrawn on a transparent Canvas so it can sit directly on any solid
 * background (e.g. the splash screen, docs/04-ui-guide.md 스플래시 결정: 배경 전체 그라디언트
 * 대신 단색 배경 위에 아이콘 마크만 짧게 노출).
 *
 * Both the launcher drawable and this Canvas draw the glyph as a **gradient stroke** now (not a
 * white/gradient fill) since both sit on a light background (white square / [DallimColors.Background]) —
 * no more "white glyph needs a colored background to show up" constraint from the old filled-silhouette
 * version, so both use the same shoe-outline/sole-seam/lace-tick/heel-tab path shapes.
 *
 * This Canvas is **not** clipped by the adaptive-icon circular mask the launcher icon goes through
 * (only `ic_launcher_foreground.xml` is), so it keeps the fuller composition the launcher had to trim
 * in its 2026-09-03 6차 수정 — motion lines, and the location pin at its original (unshrunk) size —
 * using the `x*0.95+4, y*0.95+13` bake instead of the launcher's tighter 0.67-scale safe-zone fit.
 * The two are no longer pixel-identical; keep both in sync by hand when the base shoe shape changes.
 */
@Composable
fun DallimMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val scaleX = size.width / 108f
        val scaleY = size.height / 108f
        fun pt(x: Float, y: Float) = Offset((x * 0.95f + 4f) * scaleX, (y * 0.95f + 13f) * scaleY)
        fun Path.moveTo(p: Offset) = moveTo(p.x, p.y)
        fun Path.lineTo(p: Offset) = lineTo(p.x, p.y)
        fun Path.cubicTo(c1: Offset, c2: Offset, end: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)

        // Stroke widths are authored in the same 108-unit space as the launcher's pathData
        // (outline 3.4, motion 3.2, sole seam 2.6, laces/tab 2.4) so they scale with the glyph.
        val unitScale = (scaleX + scaleY) / 2f
        val outlineWidth = 3.4f * unitScale
        val motionWidth = 3.2f * unitScale
        val soleWidth = 2.6f * unitScale
        val hairlineWidth = 2.4f * unitScale

        // 모션 라인(속도감) — 발끝 왼쪽
        val motionLines = Path().apply {
            moveTo(pt(9f, 38f)); lineTo(pt(16f, 38f))
            moveTo(pt(4f, 46f)); lineTo(pt(20f, 46f))
            moveTo(pt(3f, 56f)); lineTo(pt(23f, 56f))
            moveTo(pt(8f, 65f)); lineTo(pt(18f, 65f))
        }

        // 신발 메인 아웃라인 — 뾰족한 발끝 -> 갑피 -> 텅 봉우리 -> 노치 -> 힐 카운터 봉우리 -> 뒤꿈치 -> 아웃솔
        val shoeOutline = Path().apply {
            moveTo(pt(13f, 59f))
            cubicTo(pt(14f, 52f), pt(18f, 46f), pt(25f, 40f))
            cubicTo(pt(33f, 33f), pt(43f, 25f), pt(56f, 15f))
            cubicTo(pt(60f, 21f), pt(62f, 25f), pt(66f, 27f))
            cubicTo(pt(74f, 20f), pt(81f, 14f), pt(87f, 15f))
            cubicTo(pt(93f, 16f), pt(96f, 24f), pt(95f, 34f))
            cubicTo(pt(94f, 44f), pt(92f, 52f), pt(88f, 59f))
            cubicTo(pt(68f, 68f), pt(34f, 68f), pt(13f, 59f))
            close()
        }

        // 아웃솔 안쪽 심선
        val soleSeam = Path().apply {
            moveTo(pt(20f, 55f))
            cubicTo(pt(34f, 61f), pt(55f, 62f), pt(70f, 59f))
            cubicTo(pt(77f, 57.5f), pt(82f, 54f), pt(86f, 50f))
        }

        // 레이스 스티치
        val laceTicks = Path().apply {
            moveTo(pt(47f, 30f)); lineTo(pt(53f, 36f))
            moveTo(pt(52f, 26f)); lineTo(pt(58f, 32f))
            moveTo(pt(57f, 23f)); lineTo(pt(63f, 29f))
        }

        // 힐 풀탭 고리
        val heelPullTab = Path().apply {
            moveTo(pt(88f, 15f))
            cubicTo(pt(91f, 12f), pt(94f, 13f), pt(94f, 17f))
        }

        // 위치 핀 (힐 카운터 봉우리 위에 살짝 겹쳐짐, 중앙 구멍은 evenOdd로 뚫음)
        val pin = Path().apply {
            fillType = PathFillType.EvenOdd
            moveTo(pt(84f, 4f))
            cubicTo(pt(90f, 4f), pt(95f, 9f), pt(95f, 15f))
            cubicTo(pt(95f, 23f), pt(87f, 32f), pt(84f, 36f))
            cubicTo(pt(81f, 32f), pt(73f, 23f), pt(73f, 15f))
            cubicTo(pt(73f, 9f), pt(78f, 4f), pt(84f, 4f))
            close()
            moveTo(pt(84f, 8f))
            cubicTo(pt(87f, 8f), pt(89.5f, 10.5f), pt(89.5f, 13.5f))
            cubicTo(pt(89.5f, 16.5f), pt(87f, 19f), pt(84f, 19f))
            cubicTo(pt(81f, 19f), pt(78.5f, 16.5f), pt(78.5f, 13.5f))
            cubicTo(pt(78.5f, 10.5f), pt(81f, 8f), pt(84f, 8f))
            close()
        }

        // 그려지는 순서 중요: 모션 라인은 발끝(shoeOutline)과 겨우 몇 유닛 떨어져 있어, 먼저
        // 그리면 shoeOutline의 두꺼운 스트로크에 덮여 사라진다 — 반드시 나중에(위에) 그린다.
        drawPath(path = shoeOutline, brush = DallimGradient, style = Stroke(width = outlineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path = soleSeam, brush = DallimGradient, style = Stroke(width = soleWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path = laceTicks, brush = DallimGradient, style = Stroke(width = hairlineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path = heelPullTab, brush = DallimGradient, style = Stroke(width = hairlineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path = pin, brush = DallimGradient)
        drawPath(path = motionLines, brush = DallimGradient, style = Stroke(width = motionWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
