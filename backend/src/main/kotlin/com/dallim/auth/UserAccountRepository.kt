package com.dallim.auth

import com.dallim.common.IdGenerator
import com.dallim.user.AuthProvider
import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Minimal account-level persistence needed for the auth domain to function at all: create/find
 * raw `users` rows keyed by (provider, providerId) or by email, plus the password hash lookup
 * for /auth/login. Profile fields (nickname, avatar, runningExperience, ...) are intentionally
 * NOT touched here — populating those is the `user` domain's job (see user/UserModule.kt TODO),
 * which is out of scope for this round. That domain can reuse UserTable directly or extend
 * this repository later; nothing here precludes either.
 */
class UserAccountRepository(private val database: Database) {

    data class Account(
        val id: String,
        val provider: AuthProvider,
        val providerId: String,
        val email: String?,
    )

    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): Account? =
        transaction(database) {
            UserTable.selectAll()
                .where { (UserTable.provider eq provider) and (UserTable.providerId eq providerId) }
                .map { it.toAccount() }
                .singleOrNull()
        }

    /** Any provider — used for the account-linking guard (409 ACCOUNT_EXISTS_DIFFERENT_PROVIDER). */
    fun findByEmailAnyProvider(email: String): Account? =
        transaction(database) {
            UserTable.selectAll()
                .where { UserTable.email eq email }
                .map { it.toAccount() }
                .singleOrNull()
        }

    /** Returns (userId, passwordHash) for an EMAIL-provider account, or null if none exists. */
    fun findPasswordHashByEmail(email: String): Pair<String, String>? =
        transaction(database) {
            UserTable.selectAll()
                .where { (UserTable.email eq email) and (UserTable.provider eq AuthProvider.EMAIL) }
                .map { it[UserTable.id] to it[UserTable.passwordHash] }
                .singleOrNull()
                ?.let { (id, hash) -> hash?.let { id to it } }
        }

    fun createSocialAccount(provider: AuthProvider, providerId: String, email: String?): Account =
        transaction(database) {
            val id = IdGenerator.user()
            UserTable.insert {
                it[UserTable.id] = id
                it[UserTable.provider] = provider
                it[UserTable.providerId] = providerId
                it[UserTable.email] = email
            }
            Account(id, provider, providerId, email)
        }

    fun createEmailAccount(email: String, passwordHash: String): Account =
        transaction(database) {
            val id = IdGenerator.user()
            UserTable.insert {
                it[UserTable.id] = id
                it[UserTable.provider] = AuthProvider.EMAIL
                it[UserTable.providerId] = email
                it[UserTable.email] = email
                it[UserTable.passwordHash] = passwordHash
            }
            Account(id, AuthProvider.EMAIL, email, email)
        }

    private fun ResultRow.toAccount() =
        Account(
            id = this[UserTable.id],
            provider = this[UserTable.provider],
            providerId = this[UserTable.providerId],
            email = this[UserTable.email],
        )
}
