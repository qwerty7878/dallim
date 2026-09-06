package com.dallim.race

import kotlinx.serialization.Serializable

// GET /races, GET /races/{raceId}, POST/DELETE /races/{raceId}/save, GET /users/me/races —
// docs/02-api-spec.md 16장. gender는 어디에도 없다(CLAUDE.md 규칙 2, 애초에 이 도메인엔 무관).

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
)

/** GET /users/me/races — 날짜(raceDate) 임박순. */
@Serializable
data class MyRacesResponse(
    val items: List<RaceSummaryResponse>,
)
