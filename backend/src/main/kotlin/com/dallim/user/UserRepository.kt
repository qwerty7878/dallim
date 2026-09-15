package com.dallim.user

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

/**
 * Profile persistence for docs/02-api-spec.md 2장 (nickname-check / POST /users/me/profile /
 * GET /users/me). Deliberately separate from com.dallim.auth.UserAccountRepository, which owns
 * account-creation-time inserts into the same UserTable (see that file's class doc) -- this
 * repository only ever UPDATEs the profile columns of an already-existing row, never inserts.
 */
class UserRepository(private val database: Database) {

    fun nicknameExists(nickname: String): Boolean = transaction(database) {
        UserTable.selectAll().where { UserTable.nickname eq nickname }.limit(1).count() > 0
    }

    fun findById(userId: String): User? = transaction(database) {
        UserTable.selectAll().where { UserTable.id eq userId }.map { it.toUser() }.singleOrNull()
    }

    /** Batch lookup (social feedback-targets / running-mates 목록 등 N명 프로필을 한 번에
     * 조회해야 하는 곳에서 N+1을 피하려고 추가, 2026-09-13 com.dallim.social 2단계). 존재하지 않는
     * id는 결과에서 조용히 빠진다(호출부가 Map으로 묶어 쓰는 걸 전제). */
    fun findByIds(userIds: List<String>): List<User> {
        if (userIds.isEmpty()) return emptyList()
        return transaction(database) {
            UserTable.selectAll().where { UserTable.id inList userIds }.map { it.toUser() }
        }
    }

    /**
     * [userId]의 running_temperature를 [delta]만큼 조정하고 0~99 사이로 clamp한다(당근마켓
     * 매너온도 관례, 2026-09-13 com.dallim.social 2단계에서 처음 실제로 조정하는 로직이 생겼다 --
     * 이전까지는 항상 기본값 36.5를 그대로 노출만 했다). 읽고-쓰는 두 단계라 동시에 여러 감점/가산이
     * 몰리면 레이스가 있을 수 있지만, 지금 이 앱의 쓰기 빈도(세션당 최대 몇 건)에서는 무시할 수
     * 있는 수준이라 행 잠금 없이 단순하게 구현한다(과설계 금지 -- 트래픽이 커지면 그때 SQL
     * `GREATEST(LEAST(...))` 한 줄로 원자적으로 바꾸면 된다).
     */
    fun adjustRunningTemperature(userId: String, delta: Double) {
        transaction(database) {
            val current = UserTable.selectAll().where { UserTable.id eq userId }
                .map { it[UserTable.runningTemperature] }
                .singleOrNull() ?: return@transaction
            val next = (current + delta).coerceIn(0.0, 99.0)
            UserTable.update({ UserTable.id eq userId }) {
                it[UserTable.runningTemperature] = next
                it[UserTable.updatedAt] = Instant.now()
            }
        }
    }

    /**
     * Registers the onboarding profile fields onto an existing user row. Throws
     * [NicknameTakenException] if the unique index on `nickname` is violated concurrently
     * between the caller's own nickname-check and this write (TOCTOU per docs/02-api-spec.md
     * 2장 POST /users/me/profile note + CLAUDE.md rule 2 task brief) -- the unique index
     * (uq_users_nickname, see UserTable) is the actual final defense, this just translates the
     * resulting SQL exception into a domain-level signal the service layer can map to 409.
     */
    fun registerProfile(
        userId: String,
        nickname: String,
        avatarId: String,
        runningExperience: String,
        comfortablePace: String,
        gender: String,
    ) {
        try {
            transaction(database) {
                UserTable.update({ UserTable.id eq userId }) {
                    it[UserTable.nickname] = nickname
                    it[UserTable.avatarId] = avatarId
                    it[UserTable.runningExperience] = runningExperience
                    it[UserTable.comfortablePace] = comfortablePace
                    it[UserTable.gender] = gender
                    it[UserTable.updatedAt] = Instant.now()
                }
            }
        } catch (e: ExposedSQLException) {
            throw NicknameTakenException(e)
        }
    }

    /**
     * PATCH /users/me (2026-09-16 사용자 지시) — [nickname]/[avatarId] 각각 null이 아닌 값만
     * 갱신한다(둘 다 null이면 아무 것도 안 함). registerProfile과 마찬가지로 nickname unique
     * 인덱스 위반은 [NicknameTakenException]으로 변환한다.
     */
    fun updateProfileFields(userId: String, nickname: String?, avatarId: String?) {
        if (nickname == null && avatarId == null) return
        try {
            transaction(database) {
                UserTable.update({ UserTable.id eq userId }) {
                    if (nickname != null) it[UserTable.nickname] = nickname
                    if (avatarId != null) it[UserTable.avatarId] = avatarId
                    it[UserTable.updatedAt] = Instant.now()
                }
            }
        } catch (e: ExposedSQLException) {
            throw NicknameTakenException(e)
        }
    }

    private fun ResultRow.toUser() = User(
        id = this[UserTable.id],
        provider = this[UserTable.provider],
        providerId = this[UserTable.providerId],
        email = this[UserTable.email],
        passwordHash = this[UserTable.passwordHash],
        nickname = this[UserTable.nickname],
        avatarId = this[UserTable.avatarId],
        runningExperience = this[UserTable.runningExperience],
        comfortablePace = this[UserTable.comfortablePace],
        gender = this[UserTable.gender],
        totalRuns = this[UserTable.totalRuns],
        totalDistanceKm = this[UserTable.totalDistanceKm],
        runningTemperature = this[UserTable.runningTemperature],
        createdAt = this[UserTable.createdAt],
        updatedAt = this[UserTable.updatedAt],
    )
}

/**
 * Signals a unique-constraint violation on `nickname` from [UserRepository.registerProfile].
 * Caught by UserService and translated to `409 NICKNAME_TAKEN` (docs/02-api-spec.md 2장) --
 * kept as a distinct exception type (rather than the service catching ExposedSQLException
 * itself) so the persistence layer stays the only place that knows about the SQL exception type.
 */
class NicknameTakenException(cause: Throwable) : RuntimeException(cause)
