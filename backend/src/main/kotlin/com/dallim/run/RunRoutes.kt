package com.dallim.run

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
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Run endpoints — POST /runs, PATCH /runs/{runId}/status, POST /runs/{runId}/gps-batch,
 * POST /runs/{runId}/finish, GET /runs/{runId} (docs/02-api-spec.md 5장). All 🔒 (JWT required) —
 * no endpoint here is reachable without `authenticate(AUTH_JWT)`. Handlers are thin: parse the
 * request, delegate to RunService, wrap in ApiResponse; StatusPages translates any thrown
 * ApiException into the common error envelope.
 */
fun Route.runRoutes() {
    val runService by inject<RunService>()

    authenticate(AUTH_JWT) {
        route("/runs") {
            post {
                val userId = call.currentUserId()!!
                val request = call.receive<StartRunRequest>()
                val result = runService.startRun(userId, request)
                call.respond(HttpStatusCode.Created, ApiResponse.success(result))
            }

            patch("/{runId}/status") {
                val userId = call.currentUserId()!!
                val runId = call.parameters["runId"]!!
                val request = call.receive<UpdateRunStatusRequest>()
                val result = runService.updateStatus(userId, runId, request.status)
                call.respond(HttpStatusCode.OK, ApiResponse.success(result))
            }

            post("/{runId}/gps-batch") {
                val userId = call.currentUserId()!!
                val runId = call.parameters["runId"]!!
                val request = call.receive<GpsBatchRequest>()
                val result = runService.uploadGpsBatch(userId, runId, request)
                call.respond(HttpStatusCode.Accepted, ApiResponse.success(result))
            }

            post("/{runId}/finish") {
                val userId = call.currentUserId()!!
                val runId = call.parameters["runId"]!!
                val request = call.receive<FinishRunRequest>()
                val result = runService.finishRun(userId, runId, request)
                call.respond(HttpStatusCode.OK, ApiResponse.success(result))
            }

            get("/{runId}") {
                val userId = call.currentUserId()!!
                val runId = call.parameters["runId"]!!
                val result = runService.getRunDetail(userId, runId)
                call.respond(HttpStatusCode.OK, ApiResponse.success(result))
            }
        }
    }
}
