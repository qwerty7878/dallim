package com.dallim.meetup

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
 * Meetup endpoints — docs/02-api-spec.md 14장 (같이 달리기 모집), docs/01-feature-spec.md 1.8
 * (S-47/48/49). GET /routes/{routeId}/meetups is the only unauthenticated one, matching the other
 * /routes read endpoints (RouteRoutes.kt); everything else is 🔒.
 *
 * Mounted alongside routeRoutes() under /routes for the nested path, plus its own top-level
 * /meetups/{meetupId} tree — see com.dallim.plugins.Routing.
 */
fun Route.meetupRoutes() {
    val meetupService by inject<MeetupService>()

    route("/routes/{routeId}/meetups") {
        get {
            val routeId = call.parameters["routeId"]!!
            val response = meetupService.listByRoute(routeId)
            call.respond(HttpStatusCode.OK, ApiResponse.success(response))
        }

        authenticate(AUTH_JWT) {
            post {
                val routeId = call.parameters["routeId"]!!
                val userId = call.currentUserId()!!
                val request = call.receive<CreateMeetupRequest>()
                val response = meetupService.create(routeId, userId, request)
                call.respond(HttpStatusCode.Created, ApiResponse.success(response))
            }
        }
    }

    authenticate(AUTH_JWT) {
        route("/meetups/{meetupId}") {
            get {
                val meetupId = call.parameters["meetupId"]!!
                val userId = call.currentUserId()!!
                val response = meetupService.getDetail(meetupId, userId)
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }

            post("/join") {
                val meetupId = call.parameters["meetupId"]!!
                val userId = call.currentUserId()!!
                meetupService.join(meetupId, userId)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }

            post("/leave") {
                val meetupId = call.parameters["meetupId"]!!
                val userId = call.currentUserId()!!
                meetupService.leave(meetupId, userId)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }

            delete {
                val meetupId = call.parameters["meetupId"]!!
                val userId = call.currentUserId()!!
                meetupService.cancel(meetupId, userId)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }
        }
    }
}
