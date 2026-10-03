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
    // 2026-10-04 — 실사용 피드백("미리보기가 AI 티가 난다"): 앱이 네이버 지도 키를 갖고 있으면 실제 지도
    // 위에 경로를 올린 스냅샷 이미지를 쓰고, 캐시되기 전(첫 로딩)이나 실패 시에는 아래 Canvas 실루엣을
    // 그대로 보여준다. 키가 없는 빌드/프리뷰는 기존 Canvas 그대로다.
    if (LocalMapThumbnailsEnabled.current && coordinates.size >= 2) {
        MapSnapshotThumbnail(coordinates = coordinates, modifier = modifier, cornerRadius = cornerRadius) {
            RouteThumbnailCanvas(
                coordinates = coordinates,
                modifier = Modifier.fillMaxSize(),
                useGradient = useGradient,
                strokeWidth = strokeWidth,
                cornerRadius = 0.dp,
            )
        }
    } else {
        RouteThumbnailCanvas(coordinates, modifier, useGradient, strokeWidth, cornerRadius)
    }
}

@Composable
private fun RouteThumbnailCanvas(
    coordinates: List<GeoPoint>,
    modifier: Modifier,
    useGradient: Boolean,
    strokeWidth: Dp,
    cornerRadius: Dp,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(cornerRadius))
            .background(DallimColors.SurfaceMuted),
    ) {
        // 2026-10-04: 가짜 지도 느낌의 점 그리드를 제거했다. 이 Canvas는 실제 지도 스냅샷이 준비되기 전/실패 시의
        // 폴백(스켈레톤)이므로 중립 회색 면 위에 회색 경로선만 그린다.
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
                brush = SolidColor(DallimColors.TextTertiary),
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

