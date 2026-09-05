package com.dallim.meetup

import com.dallim.route.SketchRouteTable
import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * Run-together recruiting post — docs/02-api-spec.md 14장, docs/01-feature-spec.md 1.8
 * (2026-09-05, scope officially reopened by user request from the 8.3 "전체 소셜 세션 유보").
 *
 * Deliberately NOT a persistent "crew"/club: one row == one date/time recruiting post tied to a
 * single existing SketchRoute (1.8.3). No Exposed enum for [status] beyond OPEN/CANCELLED --
 * "마감"(full) and "종료"(scheduled_at passed) are computed at read time in MeetupService, never
 * persisted, per 14.1/14.2 (no batch job keeps a status column in sync with the clock).
 */
enum class MeetupStatus {
    OPEN,
    CANCELLED,
}

object MeetupTable : Table("run_meetups") {
    val id = varchar("id", 32)
    val routeId = varchar("route_id", 32).references(SketchRouteTable.id)
    val hostUserId = varchar("host_user_id", 32).references(UserTable.id)

    val scheduledAt = timestamp("scheduled_at")
    val maxParticipants = integer("max_participants")
    val description = text("description").nullable()

    val status = enumerationByName("status", 16, MeetupStatus::class).default(MeetupStatus.OPEN)

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

data class Meetup(
    val id: String,
    val routeId: String,
    val hostUserId: String,
    val scheduledAt: Instant,
    val maxParticipants: Int,
    val description: String?,
    val status: MeetupStatus,
    val createdAt: Instant,
)

/**
 * Participant roster, host included (host is auto-registered on create — 14.1/14.3). Composite
 * PK (meetup_id, user_id) doubles as the "already joined" check.
 */
object MeetupParticipantTable : Table("run_meetup_participants") {
    val meetupId = varchar("meetup_id", 32).references(MeetupTable.id)
    val userId = varchar("user_id", 32).references(UserTable.id)
    val joinedAt = timestamp("joined_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(meetupId, userId)
}
