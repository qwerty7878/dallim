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
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * 소셜 세션 1단계 엔드포인트 — docs/02-api-spec.md 17장 (S-30~S-34). GET /social-sessions와
 * GET /social-sessions/{id}는 비로그인도 조회 가능하지만 로그인 시 개인화된다
 * (`authenticate(AUTH_JWT, optional = true)`, com.dallim.route.RouteRoutes.kt와 동일 관례).
 * 나머지는 전부 🔒.
 */
fun Route.socialSessionRoutes() {
    val sessionService by inject<SocialSessionService>()

    route("/social-sessions") {
        authenticate(AUTH_JWT, optional = true) {
            get {
                val q = call.request.queryParameters
                val response = sessionService.list(
                    routeId = q["routeId"],
                    beginnerFriendly = q["beginnerFriendly"]?.toBooleanStrictOrNull(),
                    hasMinTemperature = q["hasMinTemperature"]?.toBooleanStrictOrNull(),
                    page = q["page"]?.toIntOrNull() ?: 0,
                    size = q["size"]?.toIntOrNull() ?: 20,
                )
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }

            get("/{sessionId}") {
                val sessionId = call.parameters["sessionId"]!!
                val userId = call.currentUserId()
                val response = sessionService.getDetail(sessionId, userId)
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }
        }

        authenticate(AUTH_JWT) {
            post {
                val userId = call.currentUserId()!!
                val request = call.receive<CreateSocialSessionRequest>()
                val response = sessionService.create(userId, request)
                call.respond(HttpStatusCode.Created, ApiResponse.success(response))
            }

            post("/{sessionId}/apply") {
                val sessionId = call.parameters["sessionId"]!!
                val userId = call.currentUserId()!!
                val request = call.receive<ApplySocialSessionRequest>()
                sessionService.apply(sessionId, userId, request)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }

            post("/{sessionId}/apply/cancel") {
                val sessionId = call.parameters["sessionId"]!!
                val userId = call.currentUserId()!!
                sessionService.cancelApply(sessionId, userId)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }

            get("/{sessionId}/applicants") {
                val sessionId = call.parameters["sessionId"]!!
                val userId = call.currentUserId()!!
                val response = sessionService.listApplicants(sessionId, userId)
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }

            post("/{sessionId}/applicants/{userId}/approve") {
                val sessionId = call.parameters["sessionId"]!!
                val targetUserId = call.parameters["userId"]!!
                val callerUserId = call.currentUserId()!!
                sessionService.approve(sessionId, callerUserId, targetUserId)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }

            delete("/{sessionId}") {
                val sessionId = call.parameters["sessionId"]!!
                val userId = call.currentUserId()!!
                sessionService.cancel(sessionId, userId)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }
        }
    }
}
