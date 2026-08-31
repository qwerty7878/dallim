package com.dallim.app.location

import com.dallim.ui.components.GeoPoint
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * S-23 코스 이탈 안내 — 계획 경로 대비 이격거리 계산 (docs/01-feature-spec.md §1.3).
 *
 * "서버 왕복 없이 클라이언트에서 point-to-line 거리 계산으로 즉시 반응해야 함"이라는 요구사항에
 * 맞춰 순수 Kotlin(Android 프레임워크 의존 없음)으로 작성했다 — 유닛 테스트 가능
 * ([app/src/test/kotlin/com/dallim/app/location/RouteDeviationCheckerTest.kt] 참고).
 *
 * 정밀 측지 계산(Haversine 대권거리) 대신 근사 평면 투영(equirectangular)을 쓴다 — 이탈 판정
 * 대상 거리(60m 안팎)에서는 오차가 무시할 수준이고, 매 위치 콜백마다 도는 연산이라 가벼워야 한다.
 * 최종 완주 판정(Haversine 합산)은 서버가 유일한 신뢰 소스이므로 여기서 정밀도를 더 높일 필요는 없다.
 */
object RouteDeviationChecker {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** 지점 [point]에서 폴리라인 [route]까지의 최단 거리(m). [route]가 2점 미만이면 무한대. */
    fun distanceToRouteMeters(point: GeoPoint, route: List<GeoPoint>): Double {
        if (route.size < 2) return Double.MAX_VALUE
        var min = Double.MAX_VALUE
        for (i in 0 until route.size - 1) {
            val d = pointToSegmentDistanceMeters(point, route[i], route[i + 1])
            if (d < min) min = d
        }
        return min
    }

    private fun pointToSegmentDistanceMeters(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val (px, py) = toLocalMeters(p, origin = a)
        val (bx, by) = toLocalMeters(b, origin = a)
        val segLenSq = bx * bx + by * by
        val t = if (segLenSq == 0.0) 0.0 else ((px * bx + py * by) / segLenSq).coerceIn(0.0, 1.0)
        val dx = px - bx * t
        val dy = py - by * t
        return sqrt(dx * dx + dy * dy)
    }

    /** [p]를 [origin]을 원점으로 하는 로컬 평면 좌표(m)로 투영한다. */
    private fun toLocalMeters(p: GeoPoint, origin: GeoPoint): Pair<Double, Double> {
        val originLatRad = Math.toRadians(origin.lat)
        val dLatRad = Math.toRadians(p.lat - origin.lat)
        val dLngRad = Math.toRadians(p.lng - origin.lng)
        val x = dLngRad * cos(originLatRad) * EARTH_RADIUS_M
        val y = dLatRad * EARTH_RADIUS_M
        return x to y
    }
}

/**
 * "60m 이상 이격이 15초 이상 지속" 트리거를 시간축으로 상태를 들고 판정하는 순수 클래스.
 * 위치 콜백이 올 때마다 [onLocation]을 호출한다 — Service/ViewModel 어느 쪽에서 호출해도 되도록
 * Android 의존성이 전혀 없다.
 */
class RouteDeviationTracker(
    private val thresholdMeters: Double = 60.0,
    private val sustainedMillis: Long = 15_000L,
) {
    private var deviationStartMillis: Long? = null

    /**
     * @return 현재 이격 상태가 [thresholdMeters] 이상으로 [sustainedMillis] 이상 지속되었는지.
     * 이격거리가 임계값 아래로 내려오면 지속 시간 카운트는 즉시 리셋된다.
     */
    fun onLocation(distanceMeters: Double, nowMillis: Long): Boolean {
        if (distanceMeters < thresholdMeters) {
            deviationStartMillis = null
            return false
        }
        val start = deviationStartMillis ?: nowMillis.also { deviationStartMillis = it }
        return nowMillis - start >= sustainedMillis
    }

    fun reset() {
        deviationStartMillis = null
    }
}
