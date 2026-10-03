package com.dallim.app.running.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.dallim.app.running.RunFormat
import com.dallim.ui.R as UiR
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.theme.DallimColors
import java.io.File
import java.io.FileOutputStream

/**
 * S-26 공유 카드 — `Canvas -> Bitmap -> ShareSheet` (docs/01-feature-spec.md §1.3). 순수
 * `android.graphics.Canvas`로 합성한다. 2026-10-04 개편: 그라디언트 배경과 "지도 없는 선 그림"을 없애고,
 * 위쪽에 실제 지도 스냅샷([mapBitmap])을 둥근 사각형으로 넣고 아래에 이름/지표 3칸을 둔다. 스냅샷이 아직 없으면
 * (오프라인 등) 단색 면 위에 경로선만 그리는 폴백을 쓴다. 색은 전부 [DallimColors]에서 가져온다.
 */
object ShareCardRenderer {

    private const val MARGIN = 56f

    fun render(
        context: Context,
        options: ShareCardOptions,
        actualRoute: List<GeoPoint>,
        mapBitmap: Bitmap?,
        title: String,
        distanceKm: Double,
        durationSeconds: Int,
        paceSecPerKm: Int,
    ): Bitmap {
        val width = options.ratio.widthPx
        val height = options.ratio.heightPx
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val dark = options.background == ShareCardBackground.DARK
        val bg = if (dark) DallimColors.BackgroundDark.toArgb() else DallimColors.Background.toArgb()
        val primaryText = if (dark) DallimColors.Surface.toArgb() else DallimColors.TextPrimary.toArgb()
        val secondaryText = if (dark) DallimColors.Surface.copy(alpha = 0.6f).toArgb() else DallimColors.TextSecondary.toArgb()
        canvas.drawColor(bg)

        val bold = ResourcesCompat.getFont(context, UiR.font.pretendard_bold) ?: Typeface.DEFAULT_BOLD
        val regular = ResourcesCompat.getFont(context, UiR.font.pretendard_regular) ?: Typeface.DEFAULT

        // 지도 영역 — 폭은 카드 폭 - 좌우 여백, 높이는 카드 비율에 따라.
        val mapRect = RectF(MARGIN, MARGIN, width - MARGIN, MARGIN + if (height > width) 1100f else 640f)
        val radius = 36f
        val clip = Path().apply { addRoundRect(mapRect, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        if (mapBitmap != null) {
            // 정사각 스냅샷을 영역에 center-crop.
            val scale = maxOf(mapRect.width() / mapBitmap.width, mapRect.height() / mapBitmap.height)
            val w = mapBitmap.width * scale
            val h = mapBitmap.height * scale
            val dst = RectF(mapRect.centerX() - w / 2, mapRect.centerY() - h / 2, mapRect.centerX() + w / 2, mapRect.centerY() + h / 2)
            canvas.drawBitmap(mapBitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            canvas.drawRect(mapRect, Paint().apply { color = if (dark) DallimColors.TextPrimary.toArgb() else DallimColors.SurfaceMuted.toArgb() })
            drawFallbackRoute(canvas, actualRoute, mapRect)
        }
        canvas.restore()

        // 제목
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryText; textSize = 46f; typeface = bold }
        var y = mapRect.bottom + 86f
        canvas.drawText(title, MARGIN, y, titlePaint)

        // 지표 3칸 — 값(크게) + 라벨(작게)
        val stats = buildList {
            if (options.showDistance) add("${RunFormat.km(distanceKm)}km" to "거리")
            if (options.showDuration) add(RunFormat.duration(durationSeconds.toLong()) to "시간")
            if (options.showPace) add("${RunFormat.pace(paceSecPerKm)}/km" to "페이스")
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryText; textSize = 64f; typeface = bold }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = secondaryText; textSize = 30f; typeface = regular }
        y += 100f
        val colWidth = (width - 2 * MARGIN) / maxOf(stats.size, 1)
        stats.forEachIndexed { i, (value, label) ->
            val x = MARGIN + colWidth * i
            canvas.drawText(value, x, y, valuePaint)
            canvas.drawText(label, x, y + 46f, labelPaint)
        }

        // 워터마크
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = secondaryText; textSize = 28f; typeface = regular }
        canvas.drawText("달림", MARGIN, height - MARGIN, brandPaint)
        return bitmap
    }

    /** 지도 스냅샷이 없을 때: 회색 면 위에 bounding-box 정규화로 경로선만 그린다(단색). */
    private fun drawFallbackRoute(canvas: Canvas, route: List<GeoPoint>, area: RectF) {
        if (route.size < 2) return
        val pad = 0.14f
        val padX = area.width() * pad
        val padY = area.height() * pad
        val drawW = area.width() - 2 * padX
        val drawH = area.height() - 2 * padY
        val minLng = route.minOf { it.lng }; val maxLng = route.maxOf { it.lng }
        val minLat = route.minOf { it.lat }; val maxLat = route.maxOf { it.lat }
        val lngRange = (maxLng - minLng).takeIf { it > 0.0 } ?: 1.0
        val latRange = (maxLat - minLat).takeIf { it > 0.0 } ?: 1.0
        fun project(p: GeoPoint): PointF {
            val nx = ((p.lng - minLng) / lngRange).toFloat()
            val ny = ((p.lat - minLat) / latRange).toFloat()
            return PointF(area.left + padX + nx * drawW, area.top + padY + (1f - ny) * drawH)
        }
        val path = Path()
        val first = project(route.first())
        path.moveTo(first.x, first.y)
        route.drop(1).forEach { val pt = project(it); path.lineTo(pt.x, pt.y) }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = DallimColors.Primary.toArgb()
        })
    }

    /** cacheDir/share_cards/에 PNG로 저장하고 [FileProvider] content Uri를 돌려준다 (ShareSheet용). */
    fun saveToCacheAndGetUri(context: Context, bitmap: Bitmap): android.net.Uri {
        val dir = File(context.cacheDir, "share_cards").apply { mkdirs() }
        val file = File(dir, "share_card_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
