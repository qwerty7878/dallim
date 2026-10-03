package com.dallim.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 결과/달림북 상세의 "완성된 GPS 그림" — 앱이 지도 키를 가진 빌드에서는 실제 지도 위의 경로(큰 스냅샷)를,
 * 아니면(키 없는 빌드/프리뷰) 어두운 캔버스([RunResultCanvas])를 쓴다. 지도 없이 선만 그린 화면을 없애기 위한
 * 2026-10-04 변경 — 지도가 준비되기 전에도 캔버스가 아래에 깔려 빈 칸이 보이지 않는다.
 */
@Composable
fun RunResultHero(
    coordinates: List<GeoPoint>,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1f,
) {
    if (LocalMapThumbnailsEnabled.current && coordinates.size >= 2) {
        RouteMapSnapshot(
            coordinates = coordinates,
            modifier = modifier.fillMaxWidth(),
            renderPx = 1080,
        ) {
            RunResultCanvas(
                coordinates = coordinates,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = androidx.compose.ui.unit.Dp(0f),
                aspectRatio = aspectRatio,
            )
        }
    } else {
        RunResultCanvas(
            coordinates = coordinates,
            modifier = modifier.fillMaxWidth(),
            cornerRadius = androidx.compose.ui.unit.Dp(0f),
            aspectRatio = aspectRatio,
        )
    }
}
