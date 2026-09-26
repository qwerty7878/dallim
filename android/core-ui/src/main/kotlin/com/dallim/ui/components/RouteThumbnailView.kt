package com.dallim.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimGradient

/**
 * A (lng, lat) coordinate — GeoJSON order, matching [com.dallim.network.common.GeoJsonLineString]
 * (kept dependency-free here so core-ui doesn't need to depend on core-network; callers map the
 * network DTO's `coordinates` into this before passing it in).
 */
data class GeoPoint(val lng: Double, val lat: Double)

/**
 * THE signature component of this app (docs/04-ui-guide.md §8, docs/03-design-system.md §3.2).
 * Renders the server-provided GeoJSON LineString as a Canvas silhouette — every course card
 * MUST use this, never a generic icon or clip art placeholder.
 *
 * - Background: Primary Light
 * - Stroke: Primary (or the signature gradient for emotional moments — result screen, etc.)
 * - Square aspect ratio, auto bounding-box normalized with 15% padding on every edge
 */
@Composable
fun RouteThumbnailView(
    coordinates: List<GeoPoint>,
    modifier: Modifier = Modifier,
    useGradient: Boolean = false,
    strokeWidth: Dp = 3.dp,
    cornerRadius: Dp = 16.dp, // slightly smaller than the 20dp card corner — thumbnail sits inside a card
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(cornerRadius))
            .background(DallimColors.PrimaryLight),
    ) {
        // 2026-09-26 추가 — 실사용 피드백("코스 카드가 모양만 있고 뒤에 배경이 아무것도 없어
        // 밋밋하다"): 단색 배경 위에 선 하나만 있으면 "지도 위의 경로"가 아니라 "색칠판 위의
        // 낙서"처럼 보인다. 실제 지도 타일 없이도 위치감을 주기 위해 옅은 점 그리드(그래프 용지
        // 느낌)를 항상 깐다 — 그라디언트가 아니라 기존 Primary 색을 낮은 알파로 쓰는 텍스처라
        // "그라디언트는 4곳에만"(docs/04-ui-guide.md §3) 규칙과 충돌하지 않고, 아이콘/클립아트도
        // 아니라 "제네릭 아이콘 금지" 규칙과도 무관하다. 경로가 없어도(coordinates.size < 2)
        // 이 그리드는 그린다 — 완전히 빈 단색보다 "아직 그려지지 않은 지도"에 가깝다.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawMapGridDots()
        }

        if (coordinates.size < 2) {
            // Empty/unavailable trail — leave the dot-grid background as-is rather than falling
            // back to a generic icon (docs/03-design-system.md §3.2 forbids that).
            return@Box
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingFraction = 0.15f
            val padX = this.size.width * paddingFraction
            val padY = this.size.height * paddingFraction
            val drawW = this.size.width - 2 * padX
            val drawH = this.size.height - 2 * padY

            val lngs = coordinates.map { it.lng }
            val lats = coordinates.map { it.lat }
            val minLng = lngs.min()
            val maxLng = lngs.max()
            val minLat = lats.min()
            val maxLat = lats.max()
            val lngRange = (maxLng - minLng).takeIf { it > 0.0 } ?: 1.0
            val latRange = (maxLat - minLat).takeIf { it > 0.0 } ?: 1.0

            fun project(p: GeoPoint): Offset {
                val nx = ((p.lng - minLng) / lngRange).toFloat()
                val ny = ((p.lat - minLat) / latRange).toFloat()
                // Flip Y: latitude increases upward, Canvas y increases downward.
                return Offset(padX + nx * drawW, padY + (1f - ny) * drawH)
            }

            val path = Path().apply {
                val first = project(coordinates.first())
                moveTo(first.x, first.y)
                coordinates.drop(1).forEach { p ->
                    val pt = project(p)
                    lineTo(pt.x, pt.y)
                }
            }

            drawPath(
                path = path,
                brush = if (useGradient) DallimGradient else SolidColor(DallimColors.Primary),
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/**
 * 6x6 옅은 점 그리드 — 실제 지도 타일 없이 "지도 위의 경로"라는 맥락을 준다(위 사용처 주석
 * 참고). 절대 dp가 아니라 캔버스 크기에 비례한 열 개수로 그려서 48dp 리스트 썸네일부터 328dp
 * Hero 카드까지 같은 밀도로 보인다.
 */
private fun DrawScope.drawMapGridDots() {
    val columns = 6
    val stepX = size.width / columns
    val stepY = size.height / columns
    val dotRadius = (minOf(stepX, stepY) * 0.06f).coerceAtLeast(1f)
    val dotColor = DallimColors.Primary.copy(alpha = 0.14f)
    for (row in 0 until columns) {
        for (col in 0 until columns) {
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(stepX * (col + 0.5f), stepY * (row + 0.5f)),
            )
        }
    }
}
