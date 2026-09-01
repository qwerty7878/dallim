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
