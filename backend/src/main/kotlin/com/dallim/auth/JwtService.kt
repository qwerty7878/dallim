package com.dallim.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.dallim.plugins.DallimConfig
import java.time.Instant
import java.util.Date

/**
 * Issues Access Tokens (JWT, 2h expiry — see docs/02-api-spec.md 0장). Verification of these
 * same tokens happens in plugins/Security.kt (`authenticate(AUTH_JWT)`); the secret/issuer/
 * audience here MUST match that plugin's config, which is why both read from the same
 * DallimConfig.JwtSettings.
 *
 * Refresh Tokens are intentionally NOT issued here — they are opaque random strings managed
 * by RefreshTokenStore (Redis-backed), per plugins/Security.kt's doc comment.
 */
class JwtService(private val jwtSettings: DallimConfig.JwtSettings) {
    private val algorithm = Algorithm.HMAC256(jwtSettings.secret)

    fun issueAccessToken(userId: String): String {
        val now = Instant.now()
        return JWT.create()
            .withIssuer(jwtSettings.issuer)
            .withAudience(jwtSettings.audience)
            .withClaim("userId", userId)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plusSeconds(jwtSettings.accessTokenExpiryMinutes * 60)))
            .sign(algorithm)
    }
}
