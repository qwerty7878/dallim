package com.dallim.user

import kotlinx.serialization.Serializable

// GET /users/nickname-check, POST /users/me/profile, GET /users/me — docs/02-api-spec.md 2장.
//
// IMPORTANT: gender is intentionally accepted on [ProfileRequest] (write-only, persisted server
// side) and must NEVER appear on [ProfileResponse] or [UserMeResponse] or any other response DTO
// (CLAUDE.md rule 2 / docs/02-api-spec.md 2장 notes on both POST /users/me/profile and GET
// /users/me). Field names below must match android/core-network's UserApi.kt DTOs exactly.

@Serializable
data class NicknameCheckResponse(val available: Boolean)

@Serializable
data class ProfileRequest(
    val nickname: String,
    val avatarId: String,
    val runningExperience: String,
    val comfortablePace: String,
    val gender: String,
)

@Serializable
data class ProfileResponse(val userId: String, val nickname: String)

/** PATCH /users/me (2026-09-16 사용자 지시) request body — 온보딩 1회성 등록(ProfileRequest)과
 * 별개로, 나중에 닉네임/아바타만 바꾸는 용도. 둘 다 optional, 보낸 필드만 갱신(null은 "변경 안
 * 함"). gender/runningExperience/comfortablePace는 이 엔드포인트로 바꿀 수 없다(범위 밖). */
@Serializable
data class PatchMeRequest(
    val nickname: String? = null,
    val avatarId: String? = null,
)

@Serializable
data class UserMeResponse(
    val userId: String,
    val nickname: String,
    val avatarId: String,
    val runningExperience: String,
    val comfortablePace: String,
    val totalRuns: Int,
    val totalDistanceKm: Double,
)
