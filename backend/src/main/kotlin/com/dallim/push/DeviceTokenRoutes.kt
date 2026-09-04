package com.dallim.push

import com.dallim.common.ApiResponse
import com.dallim.plugins.AUTH_JWT
import com.dallim.plugins.currentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * POST /users/me/device-tokens (docs/02-api-spec.md 10.1, 🔒). Kept in this push package rather
 * than com.dallim.user, even though the URL is nested under /users/me -- it's 알림 2단계(FCM)
 * infra (docs/01-feature-spec.md 1.7), not a profile field, and has no other /users/me endpoints
 * of its own. Same "own file, own route() block, mounted separately from userRoutes()" pattern
 * as com.dallim.user.savedRouteRoutes.
 */
fun Route.deviceTokenRoutes() {
    val deviceTokenService by inject<DeviceTokenService>()

    authenticate(AUTH_JWT) {
        route("/users/me/device-tokens") {
            post {
                val userId = call.currentUserId()!!
                val request = call.receive<DeviceTokenRequest>()
                deviceTokenService.registerToken(userId, request)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit?>(null))
            }
        }
    }
}
