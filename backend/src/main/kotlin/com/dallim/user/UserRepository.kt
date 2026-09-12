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
