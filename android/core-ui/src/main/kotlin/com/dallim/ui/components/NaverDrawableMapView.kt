package com.dallim.ui.components

import android.graphics.PointF
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dallim.ui.theme.DallimColors
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapOptions
import com.naver.maps.map.overlay.CircleOverlay
import com.naver.maps.map.overlay.PathOverlay
import kotlin.math.hypot

/**
 * S-44 "직접 그리기" 전용 지도 — [NaverRouteMapView]가 완성된 경로를 보여주기만 하는 것과 달리,
 * 손가락 드래그로 지도 위에 그림을 그릴 수 있다 (docs/01-feature-spec.md에 없던 새 화면이라
 * S-43/44/45로 신설 — 오케스트레이터 지시).
 *
 * - 지도 자체의 팬/줌/회전/기울기 제스처는 항상 꺼져 있다(이 컴포저블은 그리기 전용 화면에서만
 *   쓰이므로 "그리기 모드"와 "보기 모드"를 토글할 필요가 없다) — 대신 [Modifier.pointerInput]으로
 *   드래그를 직접 캡처해 [NaverMap.projection.fromScreenLocation]으로 화면 좌표를 위경도로
 *   변환한다.
 * - 점을 매 프레임 추가하면 요청 페이로드가 지나치게 커지므로, 직전에 채택한 화면 좌표로부터
 *   [minSampleDistance] 이상 이동했을 때만 새 점을 채택한다.
 * - 그려진 점들([drawnPoints])은 이 컴포저블이 아니라 호출부(ViewModel)가 소유한다 — "지우기"는
 *   호출부가 리스트를 비우기만 하면 되고, 이 컴포저블은 그 결과를 다시 그릴 뿐이다.
 */
@Composable
fun NaverDrawableMapView(
    drawnPoints: List<GeoPoint>,
    onPointDrawn: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
    initialCenter: GeoPoint? = null,
    strokeWidth: Dp = 5.dp,
    minSampleDistance: Dp = 8.dp,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(
            context,
            NaverMapOptions()
                // 2026-10-05 전면 다크 전환: 야간 모드는 Navi 지도 타입에서만 적용된다(네이버 지도 SDK).
                .mapType(NaverMap.MapType.Navi)
                .nightModeEnabled(true)
                .locationButtonEnabled(false)
                // 2026-10-04: 기본 줌 +/- 버튼과 축척자는 "기본 지도 SDK" 인상이 강해 숨긴다(핀치로 줌).
                .zoomControlEnabled(false)
                .scaleBarEnabled(false),
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
    var hasCentered by remember { mutableStateOf(false) }
    val drawnOverlay = remember { PathOverlay() }
    val startDot = remember { CircleOverlay() }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.locale = java.util.Locale.KOREAN
            // 지면을 눌러 어둡게 하고 POI 심볼을 줄여 내가 그린 선이 묻히지 않게 한다(2026-10-05).
            map.lightness = -0.3f
            map.symbolScale = 0.6f
            // 그리기 전용 지도 — 팬/줌/회전/기울기 제스처를 모두 끄고 드래그는 아래
            // pointerInput 오버레이가 전담한다.
            map.uiSettings.isScrollGesturesEnabled = false
            map.uiSettings.isZoomGesturesEnabled = false
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isTiltGesturesEnabled = false
            map.uiSettings.isStopGesturesEnabled = false
            naverMap = map
        }
    }

    LaunchedEffect(naverMap, initialCenter) {
        val map = naverMap ?: return@LaunchedEffect
        if (!hasCentered && initialCenter != null) {
            map.moveCamera(CameraUpdate.scrollAndZoomTo(LatLng(initialCenter.lat, initialCenter.lng), 16.0))
            hasCentered = true
        }
    }

    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.roundToPx() }.coerceAtLeast(1)

    LaunchedEffect(naverMap, drawnPoints, strokeWidthPx) {
        val map = naverMap ?: return@LaunchedEffect
        if (drawnPoints.size >= 2) {
            drawnOverlay.coords = drawnPoints.map { LatLng(it.lat, it.lng) }
            drawnOverlay.width = strokeWidthPx
            drawnOverlay.color = DallimColors.Primary.toArgb()
            drawnOverlay.outlineWidth = 0
            drawnOverlay.map = map
        } else {
            drawnOverlay.map = null
        }

        val start = drawnPoints.firstOrNull()
        if (start != null) {
            startDot.center = LatLng(start.lat, start.lng)
            startDot.radius = 6.0
            startDot.color = DallimColors.White.toArgb()
            startDot.outlineWidth = (strokeWidthPx / 2).coerceAtLeast(1)
            startDot.outlineColor = DallimColors.Primary.toArgb()
            startDot.map = map
        } else {
            startDot.map = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            drawnOverlay.map = null
            startDot.map = null
        }
    }

    val minSampleDistancePx = with(density) { minSampleDistance.toPx() }

    Box(modifier = modifier.background(DallimColors.BackgroundDark)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // 지도 위에 겹치는 투명 오버레이로 드래그 제스처를 직접 캡처한다 — 네이티브 MapView는
        // 위에서 팬/줌 제스처가 꺼져 있으므로 충돌 없이 이 오버레이가 모든 터치를 받는다.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(naverMap) {
                    var lastSampledOffset: Offset? = null

                    detectDragGestures(
                        onDragStart = { offset ->
                            val map = naverMap ?: return@detectDragGestures
                            val point = map.projection.fromScreenLocation(PointF(offset.x, offset.y))
                            onPointDrawn(GeoPoint(lng = point.longitude, lat = point.latitude))
                            lastSampledOffset = offset
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val map = naverMap ?: return@detectDragGestures
                            val current = change.position
                            val last = lastSampledOffset
                            val movedEnough = last == null ||
                                hypot((current.x - last.x).toDouble(), (current.y - last.y).toDouble()) >= minSampleDistancePx
                            if (movedEnough) {
                                val point = map.projection.fromScreenLocation(PointF(current.x, current.y))
                                onPointDrawn(GeoPoint(lng = point.longitude, lat = point.latitude))
                                lastSampledOffset = current
                            }
                        },
                    )
                },
        )
    }
}
