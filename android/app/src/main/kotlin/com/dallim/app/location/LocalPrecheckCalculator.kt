package com.dallim.app.location

import com.dallim.ui.components.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 타임스탬프(epoch millis)가 붙은 실제 궤적 포인트 — 속도 이상 검사에 필요. */
data class TimedGeoPoint(val point: GeoPoint, val timestampMillis: Long)

/**
 * "완주 판정 로컬 프리체크(서버 검증 이전 UX용)" (docs/01-feature-spec.md §1.3):
 * 이동거리·경로 커버리지(%)·비정상 속도(>25km/h 지속)를 클라이언트에서 1차 계산해 러닝 종료
 * 직후 즉시 애니메이션/상태를 보여주는 데 쓴다.
 *
 * **최종 판정은 항상 서버(`POST /runs/{id}/finish`) 응답으로 덮어쓴다** — 이 계산 결과는
 * [FinishRunRequest.clientPrecheckStatus]로 참고용 전송되거나 업로드 대기 중 임시 표시에만
 * 쓰이고, 절대 최종 상태로 노출하지 않는다 (CLAUDE.md rule 3, docs §1.3 하단).
 *
 * 서버의 Fréchet 거리 기반 Sketch Match(§2.2 D-4)는 여기서 재현하지 않는다 — spec이 클라이언트
 * 프리체크 범위를 "이동거리/커버리지/비정상속도"로 명시적으로 한정하고 있다.
 */
object LocalPrecheckCalculator {
    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val COVERAGE_SEGMENT_RADIUS_M = 20.0
    private const val ABNORMAL_SPEED_KMH = 25.0
    private const val COMPLETED_COVERAGE_PERCENT = 90
    private const val PARTIAL_COVERAGE_PERCENT = 50

    data class Result(
        val distanceMeters: Double,
        val coveragePercent: Int,
        val hasAbnormalSpeed: Boolean,
        /** `COMPLETED` | `PARTIAL` | `ABORTED` | `UNDER_REVIEW` — 서버와 동일한 상태값 집합. */
        val status: String,
    )

    fun calculate(actualPoints: List<TimedGeoPoint>, plannedRoute: List<GeoPoint>): Result {
        val distanceMeters = totalDistanceMeters(actualPoints.map { it.point })
        val coveragePercent = coveragePercent(plannedRoute, actualPoints.map { it.point })
        val hasAbnormalSpeed = hasAbnormalSpeedSegment(actualPoints)
        val status = when {
            hasAbnormalSpeed -> "UNDER_REVIEW"
            coveragePercent >= COMPLETED_COVERAGE_PERCENT -> "COMPLETED"
            coveragePercent >= PARTIAL_COVERAGE_PERCENT -> "PARTIAL"
            else -> "ABORTED"
        }
        return Result(distanceMeters, coveragePercent, hasAbnormalSpeed, status)
    }

    /** 연속 포인트 간 Haversine 거리 합산 (서버 §2.2 D-1과 같은 방식, 클라이언트 프리뷰용). */
    fun totalDistanceMeters(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in 0 until points.size - 1) sum += haversineMeters(points[i], points[i + 1])
        return sum
    }

    /**
     * 계획 경로를 N개 세그먼트로 나누고 각 세그먼트 반경 [COVERAGE_SEGMENT_RADIUS_M] 이내에
     * 실제 GPS 포인트가 존재하는지로 커버리지 %를 낸다 (서버 §2.2 D-2와 동일한 아이디어의 근사).
     */
    fun coveragePercent(plannedRoute: List<GeoPoint>, actualPoints: List<GeoPoint>): Int {
        if (plannedRoute.size < 2 || actualPoints.isEmpty()) return 0
        val coveredCount = plannedRoute.count { segmentPoint ->
            actualPoints.any { haversineMeters(segmentPoint, it) <= COVERAGE_SEGMENT_RADIUS_M }
        }
        return ((coveredCount.toDouble() / plannedRoute.size) * 100).toInt().coerceIn(0, 100)
    }

    /** 두 포인트 간 속도가 [ABNORMAL_SPEED_KMH]를 초과하는 구간이 하나라도 있는지 (서버 §2.2 D-3 근사). */
    fun hasAbnormalSpeedSegment(points: List<TimedGeoPoint>): Boolean {
        if (points.size < 2) return false
        for (i in 0 until points.size - 1) {
            val distM = haversineMeters(points[i].point, points[i + 1].point)
            val dtSeconds = (points[i + 1].timestampMillis - points[i].timestampMillis) / 1000.0
            if (dtSeconds <= 0.0) continue
            val speedKmh = (distM / dtSeconds) * 3.6
            if (speedKmh > ABNORMAL_SPEED_KMH) return true
        }
        return false
    }

    private fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(h), sqrt(1 - h))
        return EARTH_RADIUS_M * c
    }
}
