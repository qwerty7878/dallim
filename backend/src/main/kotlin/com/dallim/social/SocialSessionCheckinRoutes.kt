package com.dallim.social

import com.dallim.common.ApiResponse
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

/** S-36 GPS 체크인 / S-37 Ready Check 엔드포인트 — docs/02-api-spec.md 17장 이어서. */
fun Route.socialSessionCheckinRoutes() {
    val checkinService by inject<SocialSessionCheckinService>()

    route("/social-sessions/{sessionId}") {
        authenticate(AUTH_JWT) {
            post("/checkin") {
                val sessionId = call.parameters["sessionId"]!!
                val userId = call.currentUserId()!!
                val request = call.receive<CheckinRequest>()
                val response = checkinService.checkin(sessionId, userId, request.lat, request.lng)
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }

            get("/ready-check") {
                val sessionId = call.parameters["sessionId"]!!
                val userId = call.currentUserId()!!
                call.respond(HttpStatusCode.OK, ApiResponse.success(checkinService.readyCheck(sessionId, userId)))
            }

            post("/start") {
                val sessionId = call.parameters["sessionId"]!!
                val userId = call.currentUserId()!!
                checkinService.start(sessionId, userId)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }

            post("/checkins/{userId}/manual-confirm") {
                val sessionId = call.parameters["sessionId"]!!
                val targetUserId = call.parameters["userId"]!!
                val callerUserId = call.currentUserId()!!
                checkinService.manualConfirm(sessionId, callerUserId, targetUserId)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }
        }
    }
}
