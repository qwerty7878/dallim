package com.dallim.user

import com.dallim.common.ApiResponse
import com.dallim.plugins.AUTH_JWT
import com.dallim.plugins.currentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Saved-route endpoints (all 🔒) — GET/POST/DELETE /users/me/saved-routes(/{routeId}),
 * docs/02-api-spec.md 2장. This round's scope is saved-routes only: nickname-check, profile
 * registration, and GET /users/me are a separate user-domain round and are NOT added here.
 */
fun Route.savedRouteRoutes() {
    val savedRouteService by inject<SavedRouteService>()

    authenticate(AUTH_JWT) {
        route("/users/me/saved-routes") {
            get {
                val userId = call.currentUserId()!!
                val q = call.request.queryParameters
                val response = savedRouteService.listSaved(
                    userId = userId,
                    page = q["page"]?.toIntOrNull() ?: 0,
                    size = q["size"]?.toIntOrNull() ?: 20,
                )
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }

            post("/{routeId}") {
                val userId = call.currentUserId()!!
                val routeId = call.parameters["routeId"]!!
                savedRouteService.save(userId, routeId)
                call.respond(HttpStatusCode.Created, ApiResponse.success<Unit>(null))
            }

            delete("/{routeId}") {
                val userId = call.currentUserId()!!
                val routeId = call.parameters["routeId"]!!
                savedRouteService.unsave(userId, routeId)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }
        }
    }
}
