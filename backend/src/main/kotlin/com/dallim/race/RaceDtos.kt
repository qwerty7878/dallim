package com.dallim.race

import com.dallim.common.GeoJsonLineString
import kotlinx.serialization.Serializable

// GET /races, GET /races/{raceId}, POST/DELETE /races/{raceId}/save, GET /users/me/races,
// GET /races/{raceId}/course — docs/02-api-spec.md 16장. gender는 어디에도 없다(CLAUDE.md
// 규칙 2, 애초에 이 도메인엔 무관).

/** GET /races/{raceId} `categories` 아이템 — 종목별 거리/참가비/정원/컷오프. */
@Serializable
data class RaceCategoryItem(
    val category: RaceCategory,
    val distanceKm: Double?,
    val feeKrw: Int?,
    val capacity: Int?,
    val cutoffMinutes: Int?,
)

/** GET /races 목록 아이템. `categories`는 종목 배지용 enum 목록만(거리/참가비 등 상세는 detail에서). */
@Serializable
data class RaceSummaryResponse(
    val raceId: String,
    val name: String,
    val region: String,
    val location: String,
    val raceDate: String,
    val dDay: Int,
    val registrationStart: String,
    val registrationEnd: String,
    val status: RaceStatus,
    val categories: List<RaceCategory>,
    val minFeeKrw: Int?,
    val maxFeeKrw: Int?,
    // "달림 러너 N명 참가 예정" — 실제 참가 검증이 아니라 이 대회를 담은(저장한) 유저 수.
    val savedCount: Int,
    val isSaved: Boolean,
    // S-85(코스 미리 달리기) 완주 진행률 — 이 대회에 공식 코스가 없으면 null(courseRouteId ==
    // null), 있으면 0~100 정수. 비로그인이면 0. 계산은 RaceService.computePreviewProgressPercents 참고.
    val previewProgressPercent: Int?,
)

@Serializable
data class RaceListResponse(
    val items: List<RaceSummaryResponse>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
)

/** GET /races/{raceId}. */
@Serializable
data class RaceDetailResponse(
    val raceId: String,
    val name: String,
    val region: String,
    val location: String,
    val raceDate: String,
    val dDay: Int,
    val registrationStart: String,
    val registrationEnd: String,
    val status: RaceStatus,
    val organizer: String,
    val souvenir: String?,
    val categories: List<RaceCategoryItem>,
    val minFeeKrw: Int?,
    val maxFeeKrw: Int?,
    val savedCount: Int,
    val isSaved: Boolean,
    val previewProgressPercent: Int?,
)

/** GET /users/me/races — 날짜(raceDate) 임박순. */
@Serializable
data class MyRacesResponse(
    val items: List<RaceSummaryResponse>,
)

/** GET /races/{raceId}/course 구간 아이템 — S-85. `routeId`가 곧 "이 구간 달리기" 액션이다:
 * 새 엔드포인트 없이 안드로이드가 이 값으로 기존 `POST /runs`를 그대로 호출한다. */
@Serializable
data class RaceCourseSegmentItem(
    val segmentId: String,
    val label: String,
    val routeId: String,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val elevationGainM: Int,
    val orderIndex: Int,
    // userId가 이 routeId로 완주(COMPLETED)한 RunRecord가 있는지. 비로그인이면 항상 false.
    val isCompleted: Boolean,
)

/**
 * GET /races/{raceId}/course — S-85 "대회 코스 미리 달리기". 이 대회에 공식 코스가 없으면
 * `hasCourse=false`뿐이고 나머지 필드는 전부 null/빈 배열이다.
 */
@Serializable
data class RaceCourseResponse(
    val hasCourse: Boolean,
    val geoJson: GeoJsonLineString? = null,
    val distanceKm: Double? = null,
    val elevationGainM: Int? = null,
    val segments: List<RaceCourseSegmentItem> = emptyList(),
    // 완주한 구간들의 distanceKm 합 / 전체 코스 distanceKm * 100, 정수 반올림. 비로그인이면 0.
    val previewProgressPercent: Int? = null,
)
