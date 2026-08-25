package com.dallim.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal

/**
 * JWT verification plugin (Access Token, 2h expiry per docs/02-api-spec.md 0장).
 * Token *issuance* (login/signup/refresh) is backend-dev's job (auth module) — this only
 * wires up the verifier so routes can do `authenticate("auth-jwt") { ... }` (the 🔒 endpoints
 * in docs/02-api-spec.md) once implemented.
 *
 * Refresh Tokens are NOT JWTs verified by this plugin — they are opaque tokens looked up
 * against Redis (rotation on use), per docs/02-api-spec.md 0장 and 01-feature-spec.md 2.2.A.
 */
const val AUTH_JWT = "auth-jwt"

fun Application.configureSecurity(config: DallimConfig) {
    val jwtConfig = config.jwt
    val algorithm = Algorithm.HMAC256(jwtConfig.secret)

    install(Authentication) {
        jwt(AUTH_JWT) {
            realm = jwtConfig.realm
            verifier(
                JWT.require(algorithm)
                    .withIssuer(jwtConfig.issuer)
                    .withAudience(jwtConfig.audience)
                    .build(),
            )
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                if (userId.isNullOrBlank()) null else JWTPrincipal(credential.payload)
            }
        }
    }
}

/**
 * Reads the `userId` claim off the current call's JWT principal, or null when the route was
 * entered via `authenticate(AUTH_JWT, optional = true)` and no valid Bearer token was supplied
 * (e.g. GET /routes, GET /routes/{id} — docs/02-api-spec.md 4장, both usable anonymously but
 * personalized when logged in). Non-optional 🔒 routes can safely `!!` this.
 */
fun ApplicationCall.currentUserId(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
