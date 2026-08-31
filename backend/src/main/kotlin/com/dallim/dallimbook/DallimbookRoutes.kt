package com.dallim.dallimbook

import com.dallim.common.ApiResponse
import com.dallim.plugins.AUTH_JWT
import com.dallim.plugins.currentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * GET /users/me/runs 🔒 — docs/02-api-spec.md 6장, the last SPEC domain (dallimbook). This is the
 * only endpoint in this section: S-41 (작품 상세) reuses the existing GET /runs/{runId}
 * (com.dallim.run.runRoutes) rather than a new route, per docs/01-feature-spec.md 1.4.
 */
fun Route.dallimbookRoutes() {
    val dallimbookService by inject<DallimbookService>()

    authenticate(AUTH_JWT) {
        route("/users/me/runs") {
            get {
                val userId = call.currentUserId()!!
                val q = call.request.queryParameters
                val response = dallimbookService.listMyRuns(
                    userId = userId,
                    statusRaw = q["status"],
                    page = q["page"]?.toIntOrNull() ?: 0,
                    size = q["size"]?.toIntOrNull() ?: 20,
                )
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }
        }
    }
}
