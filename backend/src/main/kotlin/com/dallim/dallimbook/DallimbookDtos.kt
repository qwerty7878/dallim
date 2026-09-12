package com.dallim.dallimbook

import com.dallim.common.GeoJsonLineString
import kotlinx.serialization.Serializable

// GET /users/me/runs — docs/02-api-spec.md 6장 (S-40 달림북 그리드). Field names must match
// android/core-network/src/main/kotlin/com/dallim/network/dallimbook/DallimbookApi.kt exactly.

@Serializable
data class DallimbookRunItem(
    val runId: String,
    val routeName: String,
    val distanceKm: Double,
    val completedAt: String,
    val thumbnailGeoJson: GeoJsonLineString,
)

@Serializable
data class DallimbookResponse(
    val items: List<DallimbookRunItem>,
    val totalCount: Int,
    val totalDistanceKm: Double,
    // 2026-09-12 — 사용자 요청으로 추가 (SPEC 원문에는 없던 헤더 통계). 계산 방식은
    // docs/02-api-spec.md 6장 참고: bestPaceSecPerKm은 완주 run들 중 average_pace_sec_per_km의
    // 최솟값, averagePaceSecPerKm은 총 시간/총 거리 기반 전체 평균. 완주 기록이 없으면 둘 다 null.
    val bestPaceSecPerKm: Int?,
    val averagePaceSecPerKm: Int?,
)
