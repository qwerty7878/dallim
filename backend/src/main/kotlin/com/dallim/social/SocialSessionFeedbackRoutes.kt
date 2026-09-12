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

/** S-38 세션 종료 후 평가 엔드포인트 — docs/02-api-spec.md 17장 이어서. */
fun Route.socialSessionFeedbackRoutes() {
    val feedbackService by inject<SocialSessionFeedbackService>()

    route("/social-sessions/{sessionId}") {
        authenticate(AUTH_JWT) {
            get("/feedback-targets") {
                val sessionId = call.parameters["sessionId"]!!
                val userId = call.currentUserId()!!
                call.respond(HttpStatusCode.OK, ApiResponse.success(feedbackService.listTargets(sessionId, userId)))
            }

            post("/feedback") {
                val sessionId = call.parameters["sessionId"]!!
                val userId = call.currentUserId()!!
                val request = call.receive<SubmitFeedbackRequest>()
                call.respond(HttpStatusCode.OK, ApiResponse.success(feedbackService.submit(sessionId, userId, request)))
            }
        }
    }
}
