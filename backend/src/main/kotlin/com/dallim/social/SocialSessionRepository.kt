package com.dallim.social

import com.dallim.common.IdGenerator
import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/**
 * social_sessions / social_session_applicants persistence — docs/02-api-spec.md 17장. No PostGIS
 * columns here (meeting point is plain lat/lng doubles, unlike sketch_routes.path), so this stays
 * plain Exposed DSL throughout, same convention as com.dallim.meetup.MeetupRepository. Route
 * geometry/name/distance for a session's course preview is fetched separately in
 * SocialSessionService via com.dallim.route.RouteRepository — this repository only owns the
 * social_sessions/social_session_applicants tables themselves.
 */
open class SocialSessionRepository(private val database: Database) {

    /** A session row with its host's public fields already joined in (every read path needs them). */
    data class SessionRow(
        val id: String,
        val hostUserId: String,
        val hostNickname: String,
        val hostAvatarId: String?,
        val hostRunningTemperature: Double,
        val routeId: String,
        val title: String,
        val scheduledAt: Instant,
        val minParticipants: Int,
        val maxParticipants: Int,
        val runningStyles: List<String>,
        val beginnerFriendly: Boolean,
        val minRunningTemperature: Double?,
        val genderCondition: SocialSessionGenderCondition,
        val description: String?,
        val meetingPointLat: Double,
        val meetingPointLng: Double,
        val meetingPointDescription: String?,
        val rainPolicy: SocialSessionRainPolicy,
        val status: SocialSessionStatus,
        val startedAt: Instant?,
        val createdAt: Instant,
    )

    data class ApplicantRow(
        val userId: String,
        val nickname: String,
        val avatarId: String?,
        val runningTemperature: Double,
        val comfortablePace: String?,
        val runningExperience: String?,
        val status: SocialSessionApplicantStatus,
        val message: String?,
        val appliedAt: Instant,
        val respondedAt: Instant?,
    )

    /** POST /social-sessions (17.2). */
    open fun create(
        hostUserId: String,
        routeId: String,
        title: String,
        scheduledAt: Instant,
        minParticipants: Int,
        maxParticipants: Int,
        runningStyles: List<String>,
        beginnerFriendly: Boolean,
        minRunningTemperature: Double?,
        genderCondition: SocialSessionGenderCondition,
        description: String?,
        meetingPointLat: Double,
        meetingPointLng: Double,
        meetingPointDescription: String?,
        rainPolicy: SocialSessionRainPolicy,
    ): String {
        val id = IdGenerator.socialSession()
        transaction(database) {
            SocialSessionTable.insert {
                it[SocialSessionTable.id] = id
                it[SocialSessionTable.hostUserId] = hostUserId
                it[SocialSessionTable.routeId] = routeId
                it[SocialSessionTable.title] = title
                it[SocialSessionTable.scheduledAt] = scheduledAt
                it[SocialSessionTable.minParticipants] = minParticipants
                it[SocialSessionTable.maxParticipants] = maxParticipants
                it[SocialSessionTable.runningStyles] = runningStyles.takeIf { s -> s.isNotEmpty() }?.joinToString(",")
                it[SocialSessionTable.beginnerFriendly] = beginnerFriendly
                it[SocialSessionTable.minRunningTemperature] = minRunningTemperature
                it[SocialSessionTable.genderCondition] = genderCondition
                it[SocialSessionTable.description] = description
                it[SocialSessionTable.meetingPointLat] = meetingPointLat
                it[SocialSessionTable.meetingPointLng] = meetingPointLng
                it[SocialSessionTable.meetingPointDescription] = meetingPointDescription
                it[SocialSessionTable.rainPolicy] = rainPolicy
                it[SocialSessionTable.status] = SocialSessionStatus.RECRUITING
            }
        }
        return id
    }

    /** GET /social-sessions (17.1). [routeId]/[beginnerFriendly]/[hasMinTemperature] all optional
     * filters -- absent means "don't filter on this". Soonest-scheduled first. */
    fun list(
        routeId: String?,
        beginnerFriendly: Boolean?,
        hasMinTemperature: Boolean?,
        page: Int,
        size: Int,
    ): Pair<List<SessionRow>, Int> = transaction(database) {
        // Built twice (count / items) rather than reusing one Query object across .count() and
        // .limit()/.offset() -- same convention as com.dallim.notification.NotificationRepository
        // .findPage, safer than relying on Query being re-iterable after a terminal op.
        fun filtered() = (SocialSessionTable innerJoin UserTable)
            .selectAll()
            .where { SocialSessionTable.status neq SocialSessionStatus.CANCELLED }
            .let { q -> if (routeId != null) q.andWhere { SocialSessionTable.routeId eq routeId } else q }
            .let { q -> if (beginnerFriendly != null) q.andWhere { SocialSessionTable.beginnerFriendly eq beginnerFriendly } else q }
            .let { q ->
                when (hasMinTemperature) {
                    true -> q.andWhere { SocialSessionTable.minRunningTemperature.isNotNull() }
                    false -> q.andWhere { SocialSessionTable.minRunningTemperature.isNull() }
                    null -> q
                }
            }

        val totalCount = filtered().count().toInt()
        val items = filtered()
            .orderBy(SocialSessionTable.scheduledAt, SortOrder.ASC)
            .limit(size)
            .offset((page * size).toLong())
            .map { it.toSessionRow() }
        items to totalCount
    }

    /** GET /social-sessions/{id} / apply / approve / cancel — null when it doesn't exist (17.3,
     * any action 404s as SESSION_NOT_FOUND). */
    fun findById(sessionId: String): SessionRow? = transaction(database) {
        (SocialSessionTable innerJoin UserTable)
            .selectAll()
            .where { SocialSessionTable.id eq sessionId }
            .limit(1)
            .map { it.toSessionRow() }
            .singleOrNull()
    }

    fun countApproved(sessionId: String): Int = transaction(database) {
        SocialSessionApplicantTable.selectAll()
            .where {
                (SocialSessionApplicantTable.sessionId eq sessionId) and
                    (SocialSessionApplicantTable.status eq SocialSessionApplicantStatus.APPROVED)
            }
            .count()
            .toInt()
    }

    /** Batch APPROVED counts for a set of sessions (single GROUP BY-shaped query) -- used by the
     * list endpoint to avoid one COUNT(*) per row (N+1), same convention as
     * com.dallim.meetup.MeetupRepository.countParticipantsByMeetup. */
    fun countApprovedByIds(sessionIds: List<String>): Map<String, Int> {
        if (sessionIds.isEmpty()) return emptyMap()
        return transaction(database) {
            SocialSessionApplicantTable.selectAll()
                .where {
                    (SocialSessionApplicantTable.sessionId inList sessionIds) and
                        (SocialSessionApplicantTable.status eq SocialSessionApplicantStatus.APPROVED)
                }
                .map { it[SocialSessionApplicantTable.sessionId] }
                .groupingBy { it }
                .eachCount()
        }
    }

    fun findApplicant(sessionId: String, userId: String): ApplicantRow? = transaction(database) {
        (SocialSessionApplicantTable innerJoin UserTable)
            .selectAll()
            .where { (SocialSessionApplicantTable.sessionId eq sessionId) and (SocialSessionApplicantTable.userId eq userId) }
            .limit(1)
            .map { it.toApplicantRow() }
            .singleOrNull()
    }

    /** GET /social-sessions/{id}/applicants (17.6) -- withdrawn(CANCELLED) applicants are excluded,
     * oldest-first (queue order for the host to work through). */
    fun findApplicants(sessionId: String): List<ApplicantRow> = transaction(database) {
        (SocialSessionApplicantTable innerJoin UserTable)
            .selectAll()
            .where {
                (SocialSessionApplicantTable.sessionId eq sessionId) and
                    (SocialSessionApplicantTable.status neq SocialSessionApplicantStatus.CANCELLED)
            }
            .orderBy(SocialSessionApplicantTable.appliedAt, SortOrder.ASC)
            .map { it.toApplicantRow() }
    }

    /** GET /social-sessions/{id}'s `participants` (APPROVED only). */
    fun findApprovedApplicants(sessionId: String): List<ApplicantRow> = transaction(database) {
        (SocialSessionApplicantTable innerJoin UserTable)
            .selectAll()
            .where {
                (SocialSessionApplicantTable.sessionId eq sessionId) and
                    (SocialSessionApplicantTable.status eq SocialSessionApplicantStatus.APPROVED)
            }
            .orderBy(SocialSessionApplicantTable.respondedAt, SortOrder.ASC)
            .map { it.toApplicantRow() }
    }

    /** POST /social-sessions/{id}/apply (17.4) success path. */
    open fun addApplicant(sessionId: String, userId: String, message: String?) {
        transaction(database) {
            SocialSessionApplicantTable.insert {
                it[SocialSessionApplicantTable.sessionId] = sessionId
                it[SocialSessionApplicantTable.userId] = userId
                it[SocialSessionApplicantTable.status] = SocialSessionApplicantStatus.PENDING
                it[SocialSessionApplicantTable.message] = message
            }
        }
    }

    /** POST /social-sessions/{id}/apply/cancel (17.5) and .../approve (17.7) -- both just flip
     * [SocialSessionApplicantTable.status] + stamp [SocialSessionApplicantTable.respondedAt]. */
    fun updateApplicantStatus(sessionId: String, userId: String, status: SocialSessionApplicantStatus) {
        transaction(database) {
            SocialSessionApplicantTable.update({
                (SocialSessionApplicantTable.sessionId eq sessionId) and (SocialSessionApplicantTable.userId eq userId)
            }) {
                it[SocialSessionApplicantTable.status] = status
                it[SocialSessionApplicantTable.respondedAt] = Instant.now()
            }
        }
    }

    /** DELETE /social-sessions/{id} (17.8) -- soft-cancel only, never a real row delete, same
     * convention as com.dallim.meetup.MeetupRepository.cancel. */
    fun cancel(sessionId: String) {
        transaction(database) {
            SocialSessionTable.update({ SocialSessionTable.id eq sessionId }) {
                it[SocialSessionTable.status] = SocialSessionStatus.CANCELLED
            }
        }
    }

    /** POST /social-sessions/{id}/start (S-37) -- atomic "only if not already started" update
     * (WHERE started_at IS NULL) so a double-submit race can't silently reset the clock; returns
     * false when it was already started (caller maps that to 409 SESSION_ALREADY_STARTED). */
    fun markStarted(sessionId: String, now: Instant): Boolean = transaction(database) {
        val updated = SocialSessionTable.update({
            (SocialSessionTable.id eq sessionId) and SocialSessionTable.startedAt.isNull()
        }) {
            it[SocialSessionTable.startedAt] = now
        }
        updated > 0
    }

    /**
     * S-35 Quick Message("오늘 참가 어려워요") 취소 처리용 -- [userId]의 지난 [since] 이후
     * "당일 취소"(응답 시각과 세션 일정이 같은 날, UTC 기준 근사) 건수를 센다. 세션 전체를 대상으로
     * 하므로(현재 세션만이 아니라) 과거 다른 세션에서의 당일 취소도 함께 집계된다 -- SocialSession
     * ChatService.handleCantMakeItWithdrawal 문서 참고.
     */
    fun countSameDayCancellationsSince(userId: String, since: Instant): Int = transaction(database) {
        (SocialSessionApplicantTable innerJoin SocialSessionTable)
            .select(SocialSessionApplicantTable.respondedAt, SocialSessionTable.scheduledAt)
            .where {
                (SocialSessionApplicantTable.userId eq userId) and
                    (SocialSessionApplicantTable.status eq SocialSessionApplicantStatus.CANCELLED) and
                    (SocialSessionApplicantTable.respondedAt greaterEq since)
            }
            .count { row ->
                val respondedAt = row[SocialSessionApplicantTable.respondedAt] ?: return@count false
                val scheduledAt = row[SocialSessionTable.scheduledAt]
                respondedAt.truncatedTo(java.time.temporal.ChronoUnit.DAYS) == scheduledAt.truncatedTo(java.time.temporal.ChronoUnit.DAYS)
            }
    }

    private fun ResultRow.toSessionRow() = SessionRow(
        id = this[SocialSessionTable.id],
        hostUserId = this[SocialSessionTable.hostUserId],
        hostNickname = this[UserTable.nickname] ?: "달림이",
        hostAvatarId = this[UserTable.avatarId],
        hostRunningTemperature = this[UserTable.runningTemperature],
        routeId = this[SocialSessionTable.routeId],
        title = this[SocialSessionTable.title],
        scheduledAt = this[SocialSessionTable.scheduledAt],
        minParticipants = this[SocialSessionTable.minParticipants],
        maxParticipants = this[SocialSessionTable.maxParticipants],
        runningStyles = this[SocialSessionTable.runningStyles]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
        beginnerFriendly = this[SocialSessionTable.beginnerFriendly],
        minRunningTemperature = this[SocialSessionTable.minRunningTemperature],
        genderCondition = this[SocialSessionTable.genderCondition],
        description = this[SocialSessionTable.description],
        meetingPointLat = this[SocialSessionTable.meetingPointLat],
        meetingPointLng = this[SocialSessionTable.meetingPointLng],
        meetingPointDescription = this[SocialSessionTable.meetingPointDescription],
        rainPolicy = this[SocialSessionTable.rainPolicy],
        status = this[SocialSessionTable.status],
        startedAt = this[SocialSessionTable.startedAt],
        createdAt = this[SocialSessionTable.createdAt],
    )

    private fun ResultRow.toApplicantRow() = ApplicantRow(
        userId = this[SocialSessionApplicantTable.userId],
        nickname = this[UserTable.nickname] ?: "달림이",
        avatarId = this[UserTable.avatarId],
        runningTemperature = this[UserTable.runningTemperature],
        comfortablePace = this[UserTable.comfortablePace],
        runningExperience = this[UserTable.runningExperience],
        status = this[SocialSessionApplicantTable.status],
        message = this[SocialSessionApplicantTable.message],
        appliedAt = this[SocialSessionApplicantTable.appliedAt],
        respondedAt = this[SocialSessionApplicantTable.respondedAt],
    )
}
