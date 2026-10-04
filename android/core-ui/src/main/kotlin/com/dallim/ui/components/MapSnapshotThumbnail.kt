package com.dallim.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dallim.ui.theme.DallimColors
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapOptions
import com.naver.maps.map.overlay.CircleOverlay
import com.naver.maps.map.overlay.PathOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest

/**
 * 앱이 네이버 지도 키를 가진 빌드에서만 true로 제공한다(app 모듈 MainActivity).
 * 기본값 false라 프리뷰/테스트/키 없는 빌드는 [RouteThumbnailView]의 기존 Canvas 실루엣을 쓴다.
 */
val LocalMapThumbnailsEnabled = staticCompositionLocalOf { false }

/**
 * 실제 네이버 지도 위에 경로를 올린 정적 썸네일.
 *
 * 흐름: 메모리 캐시 -> 디스크 캐시 -> (둘 다 없으면) 화면에 보이는 동안만 [MapView]를 만들어 지도가
 * 다 로드되면 [NaverMap.takeSnapshot]으로 비트맵을 얻고 두 캐시에 저장한 뒤 MapView를 버린다.
 * 동시에 렌더하는 지도는 [MapSnapshotRenderGate]로 제한하고(리스트에 카드가 여러 장이어도 지도 인스턴스가
 * 폭증하지 않게), 10초 안에 스냅샷을 못 얻으면(오프라인/키 문제) 이번 실행 동안은 재시도하지 않고
 * [fallback](기존 Canvas 실루엣)을 그대로 둔다. 스냅샷이 준비되기 전에도 [fallback]이 아래에 깔려 있어
 * 빈 칸이 보이지 않는다.
 */
@Composable
internal fun MapSnapshotThumbnail(
    coordinates: List<GeoPoint>,
    modifier: Modifier,
    cornerRadius: Dp,
    renderPx: Int? = null,
    fallback: @Composable () -> Unit,
) = RouteMapSnapshot(coordinates, modifier, cornerRadius, renderPx = renderPx, onBitmap = null, fallback = fallback)

/**
 * 실제 지도 위에 경로를 올린 정사각 이미지. 썸네일([MapSnapshotThumbnail])과 같은 캐시/렌더 큐를 쓰되,
 * 결과 화면 히어로/공유 카드처럼 큰 크기가 필요하면 [renderPx]로 렌더 해상도를 고정하고(예: 1080),
 * 이미지가 준비되면 [onBitmap]으로 비트맵을 돌려준다(공유 카드 합성용).
 */
@Composable
fun RouteMapSnapshot(
    coordinates: List<GeoPoint>,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = androidx.compose.ui.unit.Dp(0f),
    renderPx: Int? = null,
    onBitmap: ((Bitmap) -> Unit)? = null,
    paddingFraction: Float = 0.17f,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(cornerRadius)),
    ) {
        // 작은 썸네일(56dp)도 큰 해상도로 한 번 렌더해 축소 표시한다 — 지도 로고/라벨이 픽셀 고정 크기라
        // 작게 직접 렌더하면 로고가 화면 대부분을 차지하고 경로 영역이 사라진다. 버킷 2개(480/640)만 써서
        // 같은 코스가 크기만 다른 여러 카드에서 캐시를 공유한다.
        val displayPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val sizePx = renderPx ?: if (displayPx <= RENDER_SMALL_PX) RENDER_SMALL_PX else RENDER_LARGE_PX
        val key = remember(coordinates, sizePx) { MapSnapshotCache.key(coordinates, sizePx, paddingFraction) }
        var bitmap by remember(key) { mutableStateOf(MapSnapshotCache.getMemory(key)) }
        val currentOnBitmap by androidx.compose.runtime.rememberUpdatedState(onBitmap)
        LaunchedEffect(bitmap) { bitmap?.let { currentOnBitmap?.invoke(it) } }
        var renderRequested by remember(key) { mutableStateOf(false) }

        LaunchedEffect(key) {
            if (bitmap != null) return@LaunchedEffect
            MapSnapshotCache.loadDisk(context, key)?.let {
                MapSnapshotCache.putMemory(key, it)
                bitmap = it
                return@LaunchedEffect
            }
            if (MapSnapshotCache.hasFailed(key)) return@LaunchedEffect
            MapSnapshotRenderGate.permits.withPermit {
                renderRequested = true
                withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) { snapshotFlow { bitmap }.first { it != null } }
                renderRequested = false
                if (bitmap == null) MapSnapshotCache.markFailed(key)
            }
        }

        fallback()

        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (renderRequested && bitmap == null) {
            SnapshotMapHost(coordinates = coordinates, renderPx = sizePx, paddingFraction = paddingFraction) { snapshot ->
                MapSnapshotCache.putMemory(key, snapshot)
                MapSnapshotCache.saveDisk(context, key, snapshot)
                bitmap = snapshot
            }
        }
    }
}

private const val SNAPSHOT_TIMEOUT_MS = 15_000L
private const val RENDER_SMALL_PX = 480
private const val RENDER_LARGE_PX = 640

/** 동시에 만드는 스냅샷용 지도 수 제한. */
private object MapSnapshotRenderGate {
    val permits = Semaphore(2)
}

/** 스냅샷만 찍고 버리는 비인터랙티브 지도. 호출부 Box 크기(=썸네일 크기) 그대로 레이아웃된다. */
@Composable
private fun SnapshotMapHost(coordinates: List<GeoPoint>, renderPx: Int, paddingFraction: Float, onSnapshot: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(
            context,
            NaverMapOptions()
                // 2026-10-05 전면 다크 전환: 야간 모드는 Navi 지도 타입에서만 적용된다(네이버 지도 SDK).
                .mapType(NaverMap.MapType.Navi)
                .nightModeEnabled(true)
                .locationButtonEnabled(false)
                .zoomControlEnabled(false)
                .scaleBarEnabled(false)
                .compassEnabled(false)
                .scrollGesturesEnabled(false)
                .zoomGesturesEnabled(false)
                .tiltGesturesEnabled(false)
                .rotateGesturesEnabled(false)
                .stopGesturesEnabled(false),
        )
    }
    val density = LocalDensity.current

    DisposableEffect(mapView) {
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView, coordinates) {
        mapView.getMapAsync { map -> configureSnapshotMap(map, coordinates, renderPx, paddingFraction, onSnapshot) }
    }

    // 지도는 렌더 해상도(renderPx)로 레이아웃해 찍고, 보이는 썸네일 크기에는 이미지로 축소 표시한다.
    val renderDp = with(density) { renderPx.toDp() }
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.wrapContentSize(unbounded = true, align = androidx.compose.ui.Alignment.TopStart).requiredSize(renderDp),
        )
    }
}

private fun configureSnapshotMap(
    map: NaverMap,
    coordinates: List<GeoPoint>,
    renderPx: Int,
    paddingFraction: Float,
    onSnapshot: (Bitmap) -> Unit,
) {
    // 모든 치수를 렌더 해상도에 비례시킨다(작게 표시돼도 선/여백 비율이 같다). 기준: 640px에서 선 15px.
    val unit = renderPx / 640f
    val latLngs = coordinates.map { LatLng(it.lat, it.lng) }
    map.locale = java.util.Locale.KOREAN
    map.isIndoorEnabled = false
    map.setLayerGroupEnabled(NaverMap.LAYER_GROUP_BUILDING, false)
    map.setLayerGroupEnabled(NaverMap.LAYER_GROUP_TRANSIT, false)
    map.maxZoom = 18.0
    // 2026-10-05: 야간 지도라도 기본 밝기/기본 심볼 크기 그대로면 상점·건물 POI 라벨이 빽빽해서 정작
    // 주인공인 궤적이 묻힌다. 지면을 더 눌러 어둡게 하고 POI 심볼/라벨을 작게 줄여 "궤적만 빛나는" 그림으로
    // 만든다(지도 자체는 그대로라 위치감은 남는다).
    map.lightness = -0.35f
    map.symbolScale = 0.5f

    // 2026-10-05 네온 궤적: 어두운 야간 지도 위에서 선이 "빛나 보이게" 넓고 옅은 글로우를 한 겹 깔고
    // 그 위에 또렷한 액센트 선을 얹는다(정적 스냅샷이라 실제 블러 대신 2겹 스트로크로 근사).
    // zIndex를 낮춰 글로우가 본선 아래에 깔리게 한다.
    val glow = PathOverlay().apply {
        coords = latLngs
        width = (26 * unit).toInt().coerceAtLeast(9)
        color = DallimColors.Primary.copy(alpha = 0.28f).toArgb()
        outlineWidth = 0
        zIndex = -1
        this.map = map
    }
    val path = PathOverlay().apply {
        coords = latLngs
        width = (11 * unit).toInt().coerceAtLeast(4)
        color = DallimColors.PrimaryGlow.toArgb()
        outlineWidth = (3 * unit).toInt().coerceAtLeast(1)
        // 어두운 지면 위에서는 외곽선도 어두워야 선이 떠 보인다(흰 외곽선은 선을 흐릿하게 만든다).
        outlineColor = DallimColors.Background.toArgb()
        this.map = map
    }

    val bounds = LatLngBounds.from(latLngs)
    // 시작(흰 점)/끝(코랄) — 경로 길이에 비례한 반지름이라 짧은 코스도 긴 코스도 비슷한 크기로 보인다.
    val radius = (bounds.northEast.distanceTo(bounds.southWest) * 0.03).coerceIn(5.0, 80.0)
    CircleOverlay().apply {
        center = latLngs.first()
        this.radius = radius
        color = DallimColors.White.toArgb()
        outlineColor = DallimColors.Background.toArgb()
        outlineWidth = (4 * unit).toInt().coerceAtLeast(2)
        this.map = map
    }
    CircleOverlay().apply {
        center = latLngs.last()
        this.radius = radius
        color = DallimColors.TrailEnd.toArgb()
        outlineColor = DallimColors.Background.toArgb()
        outlineWidth = (4 * unit).toInt().coerceAtLeast(2)
        this.map = map
    }

    // 카메라 이동 후 타일이 전부 로드되면 한 번만 스냅샷을 찍는다.
    var taken = false
    map.addOnLoadListener {
        if (!taken) {
            taken = true
            map.takeSnapshot(true) { bitmap -> onSnapshot(bitmap) }
        }
    }
    map.moveCamera(CameraUpdate.fitBounds(bounds, (renderPx * paddingFraction).toInt()))
    // 오버레이는 GC로 사라지지 않게 지도에 붙어 있는 동안 참조가 유지된다(overlay.map이 강한 참조).
    check(path.map === map && glow.map === map)
}

/** 메모리(LRU) + 디스크 캐시. 키는 경로 좌표(소수 5자리로 반올림)와 픽셀 크기, 스타일 버전의 해시. */
internal object MapSnapshotCache {
    // 6 = 2026-10-05 전면 다크(야간 지도 + 네온 궤적 + 지면 눌러 어둡게/POI 축소).
    // 올리면 기존 캐시가 무시돼 새 스타일로 다시 렌더된다.
    private const val STYLE_VERSION = 6
    private val memory = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val failed = HashSet<String>()

    fun key(coordinates: List<GeoPoint>, sizePx: Int, paddingFraction: Float = 0.17f): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("v$STYLE_VERSION|$sizePx|$paddingFraction|".toByteArray())
        coordinates.forEach { digest.update("%.5f,%.5f;".format(it.lat, it.lng).toByteArray()) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun getMemory(key: String): Bitmap? = memory.get(key)
    fun putMemory(key: String, bitmap: Bitmap) { memory.put(key, bitmap) }
    fun hasFailed(key: String): Boolean = synchronized(failed) { key in failed }
    fun markFailed(key: String) { synchronized(failed) { failed.add(key) } }

    private fun file(context: Context, key: String) = File(File(context.cacheDir, "route-map-thumbs").apply { mkdirs() }, "$key.png")

    suspend fun loadDisk(context: Context, key: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { file(context, key).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }.getOrNull()
    }

    fun saveDisk(context: Context, key: String, bitmap: Bitmap) {
        // 디스크 쓰기는 실패해도 화면에는 영향이 없다(다음 실행에서 다시 렌더할 뿐).
        Thread {
            runCatching { file(context, key).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }.start()
    }
}
