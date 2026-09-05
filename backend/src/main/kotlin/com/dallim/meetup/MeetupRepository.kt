package com.dallim.meetup

import com.dallim.common.IdGenerator
import com.dallim.route.SketchRouteTable
import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/**
 * Meetup persistence — docs/02-api-spec.md 14장. No PostGIS columns involved (unlike
 * com.dallim.route.RouteRepository / com.dallim.run.RunRepository), so this is plain Exposed DSL
 * throughout, same convention as com.dallim.notification.NotificationRepository /
 * com.dallim.user.SavedRouteRepository.
 *
 * "마감"(full)/"종료"(scheduled_at passed) are never queried for here -- they're derived in
 * MeetupService from [MeetupRow.maxParticipants]/participant counts and scheduledAt vs now(),
 * per docs/02-api-spec.md 14.1/14.2.
 */
open class MeetupRepository(private val database: Database) {

    /** A meetup row with its host's nickname already joined in (every read path needs it). */
    data class MeetupRow(
        val id: String,
        val routeId: String,
        val hostUserId: String,
        val hostNickname: String,
        val scheduledAt: Instant,
        val maxParticipants: Int,
        val description: String?,
        val status: MeetupStatus,
        val createdAt: Instant,
    )

    data class ParticipantRow(val userId: String, val nickname: String)

    /** POST /routes/{routeId}/meetups (14.3) — inserts the meetup row and auto-registers [hostUserId]
     * as its first participant in one transaction. */
    open fun create(
        routeId: String,
        hostUserId: String,
        scheduledAt: Instant,
        maxParticipants: Int,
        description: String?,
    ): String {
        val id = IdGenerator.meetup()
        transaction(database) {
            MeetupTable.insert {
                it[MeetupTable.id] = id
                it[MeetupTable.routeId] = routeId
                it[MeetupTable.hostUserId] = hostUserId
                it[MeetupTable.scheduledAt] = scheduledAt
                it[MeetupTable.maxParticipants] = maxParticipants
                it[MeetupTable.description] = description
                it[MeetupTable.status] = MeetupStatus.OPEN
            }
            MeetupParticipantTable.insert {
                it[MeetupParticipantTable.meetupId] = id
                it[MeetupParticipantTable.userId] = hostUserId
            }
        }
        return id
    }

    /** GET /routes/{routeId}/meetups — soonest scheduled first (14.2). */
    fun findByRoute(routeId: String): List<MeetupRow> = transaction(database) {
        (MeetupTable innerJoin UserTable)
            .selectAll()
            .where { MeetupTable.routeId eq routeId }
            .orderBy(MeetupTable.scheduledAt, SortOrder.ASC)
            .map { it.toMeetupRow() }
    }

    /** GET /meetups/{meetupId} / join / leave / delete — null when it doesn't exist (14.4, any
     * action 404s as MEETUP_NOT_FOUND). */
    fun findById(meetupId: String): MeetupRow? = transaction(database) {
        (MeetupTable innerJoin UserTable)
            .selectAll()
            .where { MeetupTable.id eq meetupId }
            .limit(1)
            .map { it.toMeetupRow() }
            .singleOrNull()
    }

    /** Route name lookup for GET /meetups/{meetupId}'s `routeName` -- separate from RouteRepository
     * (which is raw-SQL/PostGIS-flavoured) since only the plain `name` column is needed here. */
    fun findRouteName(routeId: String): String? = transaction(database) {
        SketchRouteTable.selectAll()
            .where { SketchRouteTable.id eq routeId }
            .limit(1)
            .map { it[SketchRouteTable.name] }
            .singleOrNull()
    }

    /** Batch participant counts for a set of meetups (single GROUP BY-shaped query) -- used by the
     * list endpoint to avoid one COUNT(*) per row (N+1). */
    fun countParticipantsByMeetup(meetupIds: List<String>): Map<String, Int> {
        if (meetupIds.isEmpty()) return emptyMap()
        return transaction(database) {
            MeetupParticipantTable
                .selectAll()
                .where { MeetupParticipantTable.meetupId inList meetupIds }
                .map { it[MeetupParticipantTable.meetupId] }
                .groupingBy { it }
                .eachCount()
        }
    }

    fun countParticipants(meetupId: String): Int = transaction(database) {
        MeetupParticipantTable.selectAll()
            .where { MeetupParticipantTable.meetupId eq meetupId }
            .count()
            .toInt()
    }

    fun isParticipant(meetupId: String, userId: String): Boolean = transaction(database) {
        MeetupParticipantTable.selectAll()
            .where { (MeetupParticipantTable.meetupId eq meetupId) and (MeetupParticipantTable.userId eq userId) }
            .limit(1)
            .count() > 0
    }

    /** GET /meetups/{meetupId}'s `participants` list, host included (14.4). */
    fun findParticipants(meetupId: String): List<ParticipantRow> = transaction(database) {
        (MeetupParticipantTable innerJoin UserTable)
            .selectAll()
            .where { MeetupParticipantTable.meetupId eq meetupId }
            .map { ParticipantRow(userId = it[UserTable.id], nickname = it[UserTable.nickname] ?: "달림이") }
    }

    /** POST /meetups/{meetupId}/join success path. */
    open fun addParticipant(meetupId: String, userId: String) {
        transaction(database) {
            MeetupParticipantTable.insert {
                it[MeetupParticipantTable.meetupId] = meetupId
                it[MeetupParticipantTable.userId] = userId
            }
        }
    }

    /** POST /meetups/{meetupId}/leave — returns whether a row was actually removed (leave is
     * idempotent per 14.4, so callers don't error on `false`). */
    fun removeParticipant(meetupId: String, userId: String): Boolean = transaction(database) {
        val deleted = MeetupParticipantTable.deleteWhere {
            it.run { (MeetupParticipantTable.meetupId eq meetupId) and (MeetupParticipantTable.userId eq userId) }
        }
        deleted > 0
    }

    /** DELETE /meetups/{meetupId} — soft-cancel, never a real row delete (1.8.2 / 14.4). */
    fun cancel(meetupId: String) {
        transaction(database) {
            MeetupTable.update({ MeetupTable.id eq meetupId }) {
                it[MeetupTable.status] = MeetupStatus.CANCELLED
            }
        }
    }

    private fun ResultRow.toMeetupRow() = MeetupRow(
        id = this[MeetupTable.id],
        routeId = this[MeetupTable.routeId],
        hostUserId = this[MeetupTable.hostUserId],
        hostNickname = this[UserTable.nickname] ?: "달림이",
        scheduledAt = this[MeetupTable.scheduledAt],
        maxParticipants = this[MeetupTable.maxParticipants],
        description = this[MeetupTable.description],
        status = this[MeetupTable.status],
        createdAt = this[MeetupTable.createdAt],
    )
}
