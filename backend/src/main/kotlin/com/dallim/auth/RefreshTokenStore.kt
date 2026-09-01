package com.dallim.auth

import io.lettuce.core.api.StatefulRedisConnection
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

/**
 * Refresh Token store backed by Redis (docs/02-api-spec.md 0장: "만료 30일, Redis에 저장 —
 * 회전 방식, 재발급 시 기존 토큰 즉시 폐기"). Tokens are opaque random strings, stored as
 * `refresh:{token} -> userId` with a TTL, so Redis expiry doubles as the
 * REFRESH_TOKEN_EXPIRED_OR_INVALID condition — no separate expiry bookkeeping needed.
 *
 * Rotation contract: [consume] atomically reads-then-deletes the old token; callers
 * (AuthService.refresh) must immediately [issue] a new one in the same request so the caller
 * always ends up with exactly one valid refresh token per session.
 */
class RefreshTokenStore(
    private val connection: StatefulRedisConnection<String, String>,
    private val ttlDays: Long,
) {
    private val random = SecureRandom()

    private fun key(token: String) = "refresh:$token"

    fun issue(userId: String): String {
        val token = generateToken()
        connection.sync().setex(key(token), Duration.ofDays(ttlDays).seconds, userId)
        return token
    }

    /** Returns the associated userId and deletes the token (rotation), or null if invalid/expired. */
    fun consume(token: String): String? {
        val userId = connection.sync().get(key(token)) ?: return null
        connection.sync().del(key(token))
        return userId
    }

    /** Deletes the token without needing its associated userId (POST /auth/logout). */
    fun revoke(token: String) {
        connection.sync().del(key(token))
    }

    private fun generateToken(): String {
        val bytes = ByteArray(48)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
