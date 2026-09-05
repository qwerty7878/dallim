package com.dallim.racerecord

import kotlinx.serialization.Serializable

// GET/POST/PATCH/DELETE /users/me/race-records[/{id}], POST /users/me/race-records/pace-suggestion
// — docs/02-api-spec.md 15장. gender는 이 도메인과 무관하지만 CLAUDE.md 규칙상 어떤 응답에도
// 등장해서는 안 되므로 아래 DTO 어디에도 포함하지 않는다.
//
// category/recordType은 요청에서는 자유 문자열로 받아 RaceRecordService에서 검증(잘못된 값은
// 400 VALIDATION_ERROR로 안내)하고, 응답에서는 실제 enum(RaceCategory/RecordType)을 그대로
// 직렬화한다 — com.dallim.run의 UpdateRunStatusRequest.status/RunStatus 관례와 동일.

@Serializable
data class CreateRaceRecordRequest(
    val raceName: String,
    val category: String,
    val distanceKm: Double? = null,
    val year: Int,
    val recordSeconds: Int? = null,
    val recordType: String? = null,
    val bibNumber: String? = null,
    val memo: String? = null,
)

/**
 * 부분 수정(PATCH) — null인 필드는 "변경하지 않음"으로 취급한다(값을 명시적으로 지우는 기능은
 * 이번 라운드 범위 밖 — SPEC에 그런 요구가 없다). raceName/category/year처럼 원래 필수인
 * 필드도 PATCH에서는 선택이며, 보내지 않으면 기존 값을 유지한다.
 */
@Serializable
data class UpdateRaceRecordRequest(
    val raceName: String? = null,
    val category: String? = null,
    val distanceKm: Double? = null,
    val year: Int? = null,
    val recordSeconds: Int? = null,
    val recordType: String? = null,
    val bibNumber: String? = null,
    val memo: String? = null,
)

/**
 * 목록/단건 응답 공통 아이템 — [isPb]는 매 조회 시 실시간 계산(같은 [category] 내
 * [recordSeconds]가 있는 이력 중 최솟값), [paceSuggestion]은 HALF/FULL이면서 recordSeconds가
 * 있을 때만 채워진다(PaceSuggestionCalculator, null이면 해당 없음).
 *
 * [verified]는 이번 라운드에서 항상 false — 자기신고 이력이며 인증 승격 경로가 없다.
 */
@Serializable
data class RaceRecordItem(
    val id: String,
    val raceName: String,
    val category: RaceCategory,
    val distanceKm: Double?,
    val year: Int,
    val recordSeconds: Int?,
    val recordType: RecordType?,
    val bibNumber: String?,
    val memo: String?,
    val verified: Boolean,
    val isPb: Boolean,
    val paceSuggestion: String?,
    val createdAt: String,
)

@Serializable
data class RaceRecordListResponse(val items: List<RaceRecordItem>)

@Serializable
data class PaceSuggestionRequest(
    val category: String,
    val recordSeconds: Int,
)

@Serializable
data class PaceSuggestionResponse(val suggestedPace: String)
