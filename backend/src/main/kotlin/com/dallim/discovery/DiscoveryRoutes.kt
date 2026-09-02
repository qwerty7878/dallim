package com.dallim.discovery

import com.dallim.common.ApiResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Course-creation endpoints — POST /routes/draw-convert, POST /routes/discovery
 * (docs/02-api-spec.md 8장). Neither is marked with the spec's 🔒 auth-required marker (unlike
 * e.g. `POST /users/me/profile 🔒`), so both are anonymous — consistent with the other
 * unauthenticated /routes endpoints in RouteRoutes.kt. Handlers stay thin; all validation and
 * the discovery retry algorithm live in DiscoveryService.
 */
fun Route.discoveryRoutes() {
    val discoveryService by inject<DiscoveryService>()

    route("/routes") {
        post("/draw-convert") {
            val request = call.receive<DrawConvertRequest>()
            val response = discoveryService.convertDrawnPath(request.drawnPath)
            call.respond(HttpStatusCode.OK, ApiResponse.success(response))
        }

        post("/discovery") {
            val request = call.receive<DiscoveryRequest>()
            val response = discoveryService.generateDiscoveryRoute(request)
            call.respond(HttpStatusCode.OK, ApiResponse.success(response))
        }
    }
}
