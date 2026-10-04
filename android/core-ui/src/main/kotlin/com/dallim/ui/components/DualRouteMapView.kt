package com.dallim.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimGradient

/**
 * S-21 Sketch Navigation의 실시간 지도 영역 (docs/01-feature-spec.md §1.3, docs/04-ui-guide.md §9).
 *
 * 네이버맵 SDK 연동판은 [NaverRouteMapView]다. 이 컴포넌트는 그 **폴백**이다 — NCP Client ID가
 * 아직 발급 전이라 `local.properties`의 `NAVER_MAP_CLIENT_ID`가 비어있으면
 * `BuildConfig.NAVER_MAP_CLIENT_ID_CONFIGURED == false`가 되고, 호출부(RunNavigationScreen 등)가
 * 자동으로 이쪽을 대신 그린다 — Client ID 없이도 크래시 없이 항상 동작해야 하므로 삭제하지 않고
 * 유지한다. [RouteThumbnailView] 패턴을 계획경로+실제궤적 이중 표시로 확장한 정적 Canvas
 * 렌더링으로 "코스 지도" 영역을 대체한다.
 *
 * 색맹 접근성 규칙(docs/04-ui-guide.md §9): 계획 경로와 실제 경로는 색상뿐 아니라 실선/점선으로도
 * 구분한다 — 계획 경로는 점선(연한 흰색), 실제 경로는 그라디언트 실선. [NaverRouteMapView]도
 * 동일한 규칙을 지킨다.
 */
@Composable
fun DualRouteMapView(
    plannedRoute: List<GeoPoint>,
    actualRoute: List<GeoPoint>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.BackgroundDark),
    ) {
        val boundsSource = if (plannedRoute.size >= 2) plannedRoute else actualRoute
        if (boundsSource.size < 2) return@Box

        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingFraction = 0.12f
            val padX = size.width * paddingFraction
            val padY = size.height * paddingFraction
            val drawW = size.width - 2 * padX
            val drawH = size.height - 2 * padY

            val allPoints = plannedRoute + actualRoute
            val lngs = allPoints.map { it.lng }
            val lats = allPoints.map { it.lat }
            val minLng = lngs.min()
            val maxLng = lngs.max()
            val minLat = lats.min()
            val maxLat = lats.max()
            val lngRange = (maxLng - minLng).takeIf { it > 0.0 } ?: 1.0
            val latRange = (maxLat - minLat).takeIf { it > 0.0 } ?: 1.0

            fun project(p: GeoPoint): Offset {
                val nx = ((p.lng - minLng) / lngRange).toFloat()
                val ny = ((p.lat - minLat) / latRange).toFloat()
                return Offset(padX + nx * drawW, padY + (1f - ny) * drawH)
            }

            fun pathOf(points: List<GeoPoint>): Path = Path().apply {
                if (points.isEmpty()) return@apply
                val first = project(points.first())
                moveTo(first.x, first.y)
                points.drop(1).forEach { lineTo(project(it).x, project(it).y) }
            }

            if (plannedRoute.size >= 2) {
                drawPath(
                    path = pathOf(plannedRoute),
                    brush = SolidColor(DallimColors.White.copy(alpha = 0.4f)),
                    style = Stroke(
                        width = strokeWidth.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(strokeWidth.toPx() * 2.5f, strokeWidth.toPx() * 2f),
                        ),
                    ),
                )
            }

            if (actualRoute.size >= 2) {
                drawPath(
                    path = pathOf(actualRoute),
                    brush = DallimGradient,
                    style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            if (actualRoute.isNotEmpty()) {
                val current = project(actualRoute.last())
                drawCircle(color = DallimColors.White, radius = strokeWidth.toPx() * 1.6f, center = current)
                drawCircle(color = DallimColors.GradientEnd, radius = strokeWidth.toPx() * 1.0f, center = current)
            }
        }
    }
}
