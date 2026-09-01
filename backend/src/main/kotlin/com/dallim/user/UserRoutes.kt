package com.dallim.user

import com.dallim.common.ApiResponse
import com.dallim.common.BadRequestException
import com.dallim.common.ErrorCodes
import com.dallim.plugins.AUTH_JWT
import com.dallim.plugins.currentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Profile endpoints — GET /users/nickname-check (public), POST /users/me/profile (🔒),
 * GET /users/me (🔒) — docs/02-api-spec.md 2장. Saved-route endpoints are mounted separately
 * (see SavedRouteRoutes.kt).
 */
fun Route.userRoutes() {
    val userService by inject<UserService>()

    get("/users/nickname-check") {
        val value = call.request.queryParameters["value"]
            ?: throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "value 쿼리 파라미터가 필요합니다.")
        call.respond(HttpStatusCode.OK, ApiResponse.success(userService.checkNickname(value)))
    }

    authenticate(AUTH_JWT) {
        route("/users/me") {
            post("/profile") {
                val userId = call.currentUserId()!!
                val request = call.receive<ProfileRequest>()
                val response = userService.registerProfile(userId, request)
                call.respond(HttpStatusCode.Created, ApiResponse.success(response))
            }

            get {
                val userId = call.currentUserId()!!
                call.respond(HttpStatusCode.OK, ApiResponse.success(userService.getMe(userId)))
            }
        }
    }
}
