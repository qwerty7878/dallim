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
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Profile endpoints — GET /users/nickname-check (public), POST /users/me/profile (🔒),
 * GET /users/me (🔒), PATCH /users/me (🔒, 2026-09-16 사용자 지시 — 온보딩 이후 닉네임/아바타만
 * 바꾸는 용도, POST /users/me/profile과 별개) — docs/02-api-spec.md 2장. Saved-route endpoints
 * are mounted separately (see SavedRouteRoutes.kt).
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

            patch {
                val userId = call.currentUserId()!!
                val request = call.receive<PatchMeRequest>()
                val response = userService.updateProfileFields(userId, request)
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }
        }
    }
}
