package com.dallim.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dallim.ui.theme.DallimColors
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapOptions
import com.naver.maps.map.overlay.CircleOverlay
import com.naver.maps.map.overlay.MultipartPathOverlay
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay

/**
 * S-16/S-21 "코스 지도" 영역의 실제 네이버맵 SDK 연동판 (docs/01-feature-spec.md §1.2/§1.3).
 *
 * NCP Client ID가 설정되지 않은 빌드에서는 이 컴포넌트를 아예 생성하지 않고 [DualRouteMapView]/
 * [RouteThumbnailView] Canvas 폴백을 쓰는 것이 호출부(app 모듈, `BuildConfig
 * .NAVER_MAP_CLIENT_ID_CONFIGURED` 분기)의 책임이다 — 이 컴포넌트 자체는 Client ID 유무를 모른다
 * (AndroidManifest.xml의 `com.naver.maps.map.NCP_KEY_ID` meta-data를 SDK가 자동으로 읽는다).
 *
 * [DualRouteMapView]와 동일한 파라미터 시그니처를 유지해 호출부에서 1:1로 교체 가능하다.
 *
 * 색맹 접근성 규칙(docs/04-ui-guide.md §9): 계획 경로와 실제 경로는 색상뿐 아니라 실선/점선으로도
 * 구분한다.
 * - 계획 경로(plannedRoute): 옅은 흰색 점선 — [PathOverlay.patternImage]로 dash 비트맵을 타일링.
 * - 실제 궤적(actualRoute): [DallimGradient]와 동일한 블루바이올렛→코랄 그라디언트 실선 —
 *   [PathOverlay]는 Compose Brush 그라디언트를 지원하지 않으므로, 구간을 잘게 나눠 색을 보간한
 *   [MultipartPathOverlay]로 근사한다.
 *
 * Compose 생명주기 <-> Android View 생명주기 매핑: [MapView]는 자체 onCreate/onStart/onResume/
 * onPause/onStop/onDestroy 콜백을 요구하므로, 호스트 [LifecycleOwner]에 [LifecycleEventObserver]를
 * 붙여 그대로 위임한다. `Lifecycle.addObserver`는 관찰자를 추가하는 즉시 현재 상태까지의 이벤트를
 * 모두 replay하므로(AndroidX Lifecycle 문서 동작), 이 컴포저블이 이미 RESUMED 상태인 화면에
 * 진입해도 onCreate/onStart/onResume이 누락되지 않는다.
 */
@Composable
fun NaverRouteMapView(
    plannedRoute: List<GeoPoint>,
    actualRoute: List<GeoPoint>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(
            context,
            NaverMapOptions()
                .mapType(NaverMap.MapType.Navi)
                .locationButtonEnabled(false),
        )
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var naverMap by remember { mutableStateOf<NaverMap?>(null) }
    val plannedOverlay = remember { PathOverlay() }
    val actualOverlay = remember { MultipartPathOverlay() }
    val currentPositionDot = remember { CircleOverlay() }
    val currentPositionHalo = remember { CircleOverlay() }

    LaunchedEffect(mapView) {
        // Overlay는 attach(.map = map) 시점에 자기 데이터(coords 등)가 이미 유효해야 한다 —
        // PathOverlay는 coords.size < 2인 채로 attach하면 즉시 IllegalStateException을 던진다.
        // 그래서 여기서는 NaverMap 참조만 저장하고, 실제 attach는 좌표가 채워지는 아래
        // LaunchedEffect(naverMap, plannedRoute, actualRoute, ...)에서 데이터 설정 "이후"에 한다.
        mapView.getMapAsync { map -> naverMap = map }
    }

    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.roundToPx() }.coerceAtLeast(1)
    val dashImage = remember(strokeWidthPx) { buildDashPatternImage(strokeWidthPx) }

    LaunchedEffect(naverMap, plannedRoute, actualRoute, strokeWidthPx) {
        val map = naverMap ?: return@LaunchedEffect

        if (plannedRoute.size >= 2) {
            plannedOverlay.coords = plannedRoute.toLatLngList()
            plannedOverlay.width = strokeWidthPx
            plannedOverlay.color = android.graphics.Color.TRANSPARENT
            plannedOverlay.outlineWidth = 0
            plannedOverlay.patternImage = dashImage
            plannedOverlay.patternInterval = strokeWidthPx * 4
            plannedOverlay.map = map
        } else {
            plannedOverlay.map = null
        }

        if (actualRoute.size >= 2) {
            val latLngs = actualRoute.toLatLngList()
            val (coordParts, colorParts) = buildGradientParts(latLngs)
            actualOverlay.coordParts = coordParts
            actualOverlay.colorParts = colorParts
            actualOverlay.width = strokeWidthPx
            actualOverlay.map = map

            val current = latLngs.last()
            currentPositionHalo.center = current
            currentPositionHalo.radius = 7.0
            currentPositionHalo.color = DallimColors.Surface.toArgb()
            currentPositionHalo.outlineWidth = 0
            currentPositionHalo.map = map
            currentPositionDot.center = current
            currentPositionDot.radius = 4.0
            currentPositionDot.color = DallimColors.GradientEnd.toArgb()
            currentPositionDot.outlineWidth = 0
            currentPositionDot.map = map
        } else {
            actualOverlay.map = null
            currentPositionHalo.map = null
            currentPositionDot.map = null
        }

        val boundsSource = (plannedRoute + actualRoute).toLatLngList()
        when {
            boundsSource.size >= 2 -> {
                val bounds = LatLngBounds.from(boundsSource)
                map.moveCamera(CameraUpdate.fitBounds(bounds, strokeWidthPx * 10))
            }
            boundsSource.size == 1 -> {
                map.moveCamera(CameraUpdate.scrollAndZoomTo(boundsSource.first(), 16.0))
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            plannedOverlay.map = null
            actualOverlay.map = null
            currentPositionHalo.map = null
            currentPositionDot.map = null
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.background(DallimColors.BackgroundDark),
    )
}

private fun List<GeoPoint>.toLatLngList(): List<LatLng> = map { LatLng(it.lat, it.lng) }

/**
 * 계획 경로를 점선으로 그리기 위한 반복 타일 비트맵. [PathOverlay]는 Compose의
 * [androidx.compose.ui.graphics.PathEffect.dashPathEffect]에 대응하는 API가 없어, 짧은 dash
 * 비트맵을 [PathOverlay.patternImage]로 타일링하는 방식으로 동일한 시각 효과를 낸다.
 */
private fun buildDashPatternImage(strokeWidthPx: Int): OverlayImage {
    val dashLength = strokeWidthPx * 3
    val gapLength = strokeWidthPx * 2
    val width = (dashLength + gapLength).coerceAtLeast(2)
    val height = strokeWidthPx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint().apply {
        isAntiAlias = true
        color = DallimColors.Surface.copy(alpha = 0.7f).toArgb()
    }
    canvas.drawRect(0f, 0f, dashLength.toFloat(), height.toFloat(), paint)
    return OverlayImage.fromBitmap(bitmap)
}

/**
 * [DallimGradient](GradientStart -> GradientEnd)를 [MultipartPathOverlay]로 근사한다 — 경로를
 * 최대 [segments]개 구간으로 나누고 구간별로 보간된 단색을 입혀 그라디언트처럼 보이게 한다.
 */
private fun buildGradientParts(
    points: List<LatLng>,
    segments: Int = 24,
): Pair<List<List<LatLng>>, List<MultipartPathOverlay.ColorPart>> {
    if (points.size < 2) return emptyList<List<LatLng>>() to emptyList()

    val segmentCount = minOf(segments, points.size - 1).coerceAtLeast(1)
    val coordParts = mutableListOf<List<LatLng>>()
    val colorParts = mutableListOf<MultipartPathOverlay.ColorPart>()

    for (i in 0 until segmentCount) {
        val startIdx = (i * (points.size - 1) / segmentCount)
        val endIdx = if (i == segmentCount - 1) points.size - 1 else ((i + 1) * (points.size - 1) / segmentCount)
        if (endIdx <= startIdx) continue

        coordParts.add(points.subList(startIdx, endIdx + 1))
        val t = if (segmentCount == 1) 0f else i.toFloat() / (segmentCount - 1)
        val color = lerp(DallimColors.GradientStart, DallimColors.GradientEnd, t).toArgb()
        colorParts.add(MultipartPathOverlay.ColorPart(color, color, color, color))
    }
    return coordParts to colorParts
}
