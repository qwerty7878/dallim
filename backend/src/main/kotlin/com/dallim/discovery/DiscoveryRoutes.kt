package com.dallim.discovery

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

/**
 * Course-creation endpoints — POST /routes/draw-convert, POST /routes/discovery,
 * GET /routes/discovery/quota, POST /routes/discovery/reward-unlock (docs/02-api-spec.md 8장).
 * Handlers stay thin; all validation and the discovery retry algorithm live in DiscoveryService,
 * the daily-quota bookkeeping in DiscoveryQuotaService.
 *
 * `draw-convert` has no 🔒 marker and stays anonymous, same as before (it isn't quota-gated —
 * only AI discovery is, per v1.3 S-12/13). `discovery` and its two quota sub-endpoints, however,
 * are 🔒 (docs/02-api-spec.md 8.4, 2026-09-06 추가) — daily quota is counted per authenticated
 * user, so generation can no longer be anonymous, same `authenticate(AUTH_JWT)` pattern as
 * com.dallim.run.RunRoutes.
 */
fun Route.discoveryRoutes() {
    val discoveryService by inject<DiscoveryService>()
    val discoveryQuotaService by inject<DiscoveryQuotaService>()

    route("/routes") {
        post("/draw-convert") {
            val request = call.receive<DrawConvertRequest>()
            val response = discoveryService.convertDrawnPath(request)
            call.respond(HttpStatusCode.OK, ApiResponse.success(response))
        }

        authenticate(AUTH_JWT) {
            post("/discovery") {
                val userId = call.currentUserId()!!
                val request = call.receive<DiscoveryRequest>()
                val response = discoveryService.generateDiscoveryRoute(request, userId)
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }

            get("/discovery/quota") {
                val userId = call.currentUserId()!!
                val response = discoveryQuotaService.getStatus(userId)
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }

            post("/discovery/reward-unlock") {
                val userId = call.currentUserId()!!
                val response = discoveryQuotaService.addRewardBonus(userId)
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }
        }
    }
}
