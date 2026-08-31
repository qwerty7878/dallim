package com.dallim.app.running.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.dallim.app.running.RunFormat
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.theme.DallimColors
import java.io.File
import java.io.FileOutputStream

/**
 * S-26 공유 카드 편집 — `Canvas -> Bitmap -> ShareSheet` (docs/01-feature-spec.md §1.3).
 * Compose Canvas가 아니라 순수 `android.graphics.Canvas`로 직접 합성한다 — Compose 컴포저블을
 * 오프스크린 Bitmap으로 캡처하는 것보다 이 방식이 더 단순하고 버전 의존성이 적다.
 *
 * 색상은 전부 [DallimColors] 토큰에서 가져와 `.toArgb()`로 변환한다 — 이 렌더러도 새 색상값을
 * 만들지 않는다(하드코딩 금지 원칙은 Compose 파일뿐 아니라 여기도 동일하게 적용).
 */
object ShareCardRenderer {

    fun render(options: ShareCardOptions, actualRoute: List<GeoPoint>, routeName: String, routeEmoji: String, distanceKm: Double, durationSeconds: Int, paceSecPerKm: Int): Bitmap {
        val width = options.ratio.widthPx
        val height = options.ratio.heightPx
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val isDarkBackground = options.background != ShareCardBackground.LIGHT
        drawBackground(canvas, options.background, width, height)

        val contentColor = if (isDarkBackground) DallimColors.Surface.toArgb() else DallimColors.TextPrimary.toArgb()
        val secondaryColor = if (isDarkBackground) {
            DallimColors.Surface.copy(alpha = 0.7f).toArgb()
        } else {
            DallimColors.TextSecondary.toArgb()
        }

        drawRoute(canvas, actualRoute, width, height)
        drawMetrics(canvas, options, width, height, contentColor, secondaryColor, routeName, routeEmoji, distanceKm, durationSeconds, paceSecPerKm)

        return bitmap
    }

    private fun drawBackground(canvas: Canvas, background: ShareCardBackground, width: Int, height: Int) {
        val paint = Paint()
        when (background) {
            ShareCardBackground.LIGHT -> paint.color = DallimColors.Background.toArgb()
            ShareCardBackground.DARK -> paint.color = DallimColors.BackgroundDark.toArgb()
            ShareCardBackground.GRADIENT -> {
                paint.shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    DallimColors.GradientStart.toArgb(), DallimColors.GradientEnd.toArgb(),
                    Shader.TileMode.CLAMP,
                )
            }
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    /** 경로 영역은 카드 상단 65%에 [RouteThumbnailView]와 같은 bounding-box 정규화로 그린다. */
    private fun drawRoute(canvas: Canvas, route: List<GeoPoint>, width: Int, height: Int) {
        if (route.size < 2) return
        val areaHeight = height * 0.6f
        val paddingFraction = 0.12f
        val padX = width * paddingFraction
        val padY = areaHeight * paddingFraction
        val drawW = width - 2 * padX
        val drawH = areaHeight - 2 * padY

        val lngs = route.map { it.lng }
        val lats = route.map { it.lat }
        val minLng = lngs.min()
        val maxLng = lngs.max()
        val minLat = lats.min()
        val maxLat = lats.max()
        val lngRange = (maxLng - minLng).takeIf { it > 0.0 } ?: 1.0
        val latRange = (maxLat - minLat).takeIf { it > 0.0 } ?: 1.0

        fun project(p: GeoPoint): PointF {
            val nx = ((p.lng - minLng) / lngRange).toFloat()
            val ny = ((p.lat - minLat) / latRange).toFloat()
            return PointF(padX + nx * drawW, padY + (1f - ny) * drawH)
        }

        val path = Path()
        val first = project(route.first())
        path.moveTo(first.x, first.y)
        route.drop(1).forEach { val pt = project(it); path.lineTo(pt.x, pt.y) }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width * 0.012f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            shader = LinearGradient(
                padX, padY, padX + drawW, padY + drawH,
                DallimColors.GradientStart.toArgb(), DallimColors.GradientEnd.toArgb(),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(path, paint)
    }

    private fun drawMetrics(
        canvas: Canvas,
        options: ShareCardOptions,
        width: Int,
        height: Int,
        contentColor: Int,
        secondaryColor: Int,
        routeName: String,
        routeEmoji: String,
        distanceKm: Double,
        durationSeconds: Int,
        paceSecPerKm: Int,
    ) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = contentColor
            textSize = width * 0.055f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            textSize = width * 0.032f
            textAlign = Paint.Align.LEFT
        }

        val marginX = width * 0.08f
        var y = height * 0.72f

        canvas.drawText("$routeEmoji $routeName", marginX, y, titlePaint)
        y += width * 0.09f

        val values = buildList {
            if (options.showDistance) add("${RunFormat.distanceKm(distanceKm * 1000)}km")
            if (options.showDuration) add(RunFormat.duration(durationSeconds.toLong()))
            if (options.showPace) add("${RunFormat.pace(paceSecPerKm)}/km")
        }
        val statPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = contentColor
            textSize = width * 0.07f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(values.joinToString("   "), marginX, y, statPaint)
        y += width * 0.06f
        canvas.drawText("달림 · Dallim", marginX, y, bodyPaint)
    }

    /** cacheDir/share_cards/에 PNG로 저장하고 [FileProvider] content Uri를 돌려준다 (ShareSheet용). */
    fun saveToCacheAndGetUri(context: Context, bitmap: Bitmap): android.net.Uri {
        val dir = File(context.cacheDir, "share_cards").apply { mkdirs() }
        val file = File(dir, "share_card_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
