package com.dallim.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimGradient

/**
 * S-25 달림 결과 — 완성된 GPS 그림을 "액자에 담긴 작품처럼" 그려낸다
 * (docs/04-ui-guide.md §8: "결과 화면의 GPS 그림은 액자에 담긴 작품처럼 크고 여백 있게. 이
 * 화면만은 다른 곳보다 화려해도 된다"). [RouteThumbnailView]와 같은 bounding-box 정규화
 * 로직을 쓰되, 정적 렌더링이 아니라 선이 그려지는(Path drawing) 애니메이션으로 재생한다.
 *
 * [animate]가 false면 애니메이션 없이 완성된 상태로 바로 그린다(예: 재방문 시).
 */
@Composable
fun RunResultCanvas(
    coordinates: List<GeoPoint>,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    animationDurationMillis: Int = 1800,
) {
    val progressAnimatable = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate, animationDurationMillis) {
        if (animate) {
            progressAnimatable.snapTo(0f)
            progressAnimatable.animateTo(1f, tween(durationMillis = animationDurationMillis, easing = LinearEasing))
        } else {
            progressAnimatable.snapTo(1f)
        }
    }
    val progress = progressAnimatable.value

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(RoundedCornerShape(24.dp))
            .background(DallimColors.PrimaryLight)
            .padding(20.dp),
    ) {
        if (coordinates.size < 2) return@Box

        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingFraction = 0.08f
            val padX = size.width * paddingFraction
            val padY = size.height * paddingFraction
            val drawW = size.width - 2 * padX
            val drawH = size.height - 2 * padY

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
                return Offset(padX + nx * drawW, padY + (1f - ny) * drawH)
            }

            val fullPath = Path().apply {
                val first = project(coordinates.first())
                moveTo(first.x, first.y)
                coordinates.drop(1).forEach { lineTo(project(it).x, project(it).y) }
            }

            val measure = PathMeasure().apply { setPath(fullPath, false) }
            val trimmedPath = Path()
            measure.getSegment(0f, measure.length * progress, trimmedPath, true)

            drawPath(
                path = trimmedPath,
                brush = DallimGradient,
                style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
