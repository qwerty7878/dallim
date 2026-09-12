package com.dallim.user

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * Login provider — see docs/02-api-spec.md 1장.
 * Account identity key is (provider, providerId): for EMAIL, providerId == email.
 */
enum class AuthProvider {
    GOOGLE,
    KAKAO,
    EMAIL,
}

object UserTable : Table("users") {
    val id = varchar("id", 32)
    val provider = enumerationByName("provider", 16, AuthProvider::class)
    val providerId = varchar("provider_id", 255)
    val email = varchar("email", 254).nullable()
    val passwordHash = varchar("password_hash", 100).nullable() // BCrypt hash, EMAIL provider only. Never expose.

    val nickname = varchar("nickname", 30).nullable()
    val avatarId = varchar("avatar_id", 32).nullable()
    // Free-form per docs/01-feature-spec.md S-04 (values not fully enumerated in SPEC yet,
    // e.g. "UNDER_3_MONTHS"). Do not invent a closed enum until SPEC defines the full set.
    val runningExperience = varchar("running_experience", 32).nullable()
    val comfortablePace = varchar("comfortable_pace", 32).nullable() // e.g. "PACE_6_7"

    // IMPORTANT: gender is persisted server-side only. It must NEVER be mapped onto any
    // response DTO (see docs/02-api-spec.md 2장 GET /users/me note, CLAUDE.md rule 2).
    // 32 to match the other free-form profile columns above -- 16 was too tight and rejected
    // the Android client's own "PREFER_NOT_TO_SAY" (17 chars) enum value in practice (found via
    // live end-to-end signup testing 2026-09-03, see V3 migration).
    val gender = varchar("gender", 32).nullable()

    val totalRuns = integer("total_runs").default(0)
    val totalDistanceKm = double("total_distance_km").default(0.0)

    // 매너온도류 신뢰도 점수 (당근마켓 매너온도와 같은 컨셉), 36.5에서 시작 -- 2026-09-13,
    // com.dallim.social 도메인(S-30~S-34)이 호스트/참가자 카드에 노출하려고 추가했다. gender와
    // 달리 이건 공개 정보다(S-30/32/34 SPEC이 명시적으로 노출을 요구). 이 필드를 조정하는 로직
    // (피드백/노쇼 반영)은 2단계(S-38)에서 만든다 -- 지금은 항상 기본값을 그대로 읽어서 응답에
    // 노출만 한다.
    val runningTemperature = double("running_temperature").default(36.5)

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_users_provider_provider_id", provider, providerId)
        uniqueIndex("uq_users_nickname", nickname)
    }
}

data class User(
    val id: String,
    val provider: AuthProvider,
    val providerId: String,
    val email: String?,
    val passwordHash: String?,
    val nickname: String?,
    val avatarId: String?,
    val runningExperience: String?,
    val comfortablePace: String?,
    val gender: String?, // server-side only, see UserTable.gender doc above
    val totalRuns: Int,
    val totalDistanceKm: Double,
    val runningTemperature: Double,
    val createdAt: Instant,
    val updatedAt: Instant,
)
