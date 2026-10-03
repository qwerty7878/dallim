package com.dallim.app.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.LocalMapThumbnailsEnabled
import com.dallim.ui.components.RouteMapSnapshot
import com.dallim.ui.components.RouteThumbnailView
import com.dallim.ui.theme.DallimShapes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 온보딩/로그인/권한 안내에 쓰는 "실제 지도 위의 GPS 그림" 샘플 경로 (2026-10-04). 손으로 그린 일러스트
 * 대신 앱이 실제로 보여주는 것(지도 위에 그려지는 코스)을 그대로 보여준다. 좌표는 서울의 실제 장소 위에
 * 파라메트릭 곡선으로 만든 장식용 경로이며 도로를 따라가지는 않는다.
 */
object OnboardingArt {
    /** 올림픽공원 인근 하트. */
    val Heart: List<GeoPoint> by lazy { heart(37.5200, 127.1215, 1100.0) }

    /** 서울숲 인근 별. */
    val Star: List<GeoPoint> by lazy { star(37.5444, 127.0374, 900.0) }

    /** 한강 반포 인근 물결. */
    val Wave: List<GeoPoint> by lazy { wave(37.5110, 126.9960, 1300.0) }

    /** 잠실 인근 원형 루프. */
    val Loop: List<GeoPoint> by lazy { loop(37.5133, 127.0860, 800.0) }

    private fun offset(lat: Double, lng: Double, dxM: Double, dyM: Double) = GeoPoint(
        lng = lng + dxM / (111_320.0 * cos(Math.toRadians(lat))),
        lat = lat + dyM / 111_320.0,
    )

    private fun heart(lat: Double, lng: Double, sizeM: Double, n: Int = 64): List<GeoPoint> = (0..n).map { i ->
        val t = 2 * PI * i / n
        val x = 16 * sin(t).pow(3)
        val y = 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)
        offset(lat, lng, x / 16 * sizeM / 2, (y + 2.5) / 17 * sizeM / 2)
    }

    private fun star(lat: Double, lng: Double, sizeM: Double): List<GeoPoint> {
        val order = listOf(0, 2, 4, 1, 3, 0)
        return order.map { k ->
            val a = PI / 2 + 2 * PI * k / 5
            offset(lat, lng, cos(a) * sizeM / 2, sin(a) * sizeM / 2)
        }
    }

    private fun wave(lat: Double, lng: Double, sizeM: Double, n: Int = 48): List<GeoPoint> = (0..n).map { i ->
        val u = i.toDouble() / n
        offset(lat, lng, (u - 0.5) * sizeM, sin(u * 2 * PI * 2) * sizeM * 0.16)
    }

    private fun loop(lat: Double, lng: Double, sizeM: Double, n: Int = 40): List<GeoPoint> = (0..n).map { i ->
        val t = 2 * PI * i / n
        offset(lat, lng, cos(t) * sizeM / 2, sin(t) * sizeM / 2 * 0.8)
    }
}

/** 실제 네이버 지도 위 샘플 경로. 지도를 못 불러오면(오프라인/키 없음) 회색 면 위 경로선으로 대체한다. */
@Composable
fun OnboardingMapArt(coordinates: List<GeoPoint>, modifier: Modifier = Modifier) {
    val shape = DallimShapes.CardCorner
    RouteMapSnapshot(
        coordinates = coordinates,
        modifier = modifier.fillMaxWidth().clip(shape),
        cornerRadius = 16.dp,
        paddingFraction = 0.22f,
    ) {
        CompositionLocalProvider(LocalMapThumbnailsEnabled provides false) {
            RouteThumbnailView(coordinates = coordinates, modifier = Modifier.fillMaxSize(), cornerRadius = 0.dp)
        }
    }
}
