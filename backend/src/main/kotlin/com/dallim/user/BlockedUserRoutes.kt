package com.dallim.user

import com.dallim.common.ApiResponse
import com.dallim.plugins.AUTH_JWT
import com.dallim.plugins.currentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * 유저 차단 endpoints (all 🔒) — POST/GET /users/me/blocks, DELETE /users/me/blocks/{blockedUserId}
 * — docs/02-api-spec.md 18장. com.dallim.user.savedRouteRoutes와 동일한 "/users/me 하위 소유물"
 * 관례.
 */
fun Route.blockedUserRoutes() {
    val blockedUserService by inject<BlockedUserService>()

    authenticate(AUTH_JWT) {
        route("/users/me/blocks") {
            get {
                val userId = call.currentUserId()!!
                call.respond(HttpStatusCode.OK, ApiResponse.success(blockedUserService.listBlocked(userId)))
            }

            post {
                val userId = call.currentUserId()!!
                val request = call.receive<BlockUserRequest>()
                blockedUserService.block(userId, request)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }

            delete("/{blockedUserId}") {
                val userId = call.currentUserId()!!
                val blockedUserId = call.parameters["blockedUserId"]!!
                blockedUserService.unblock(userId, blockedUserId)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }
        }
    }
}
