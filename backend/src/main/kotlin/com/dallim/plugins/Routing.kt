package com.dallim.plugins

import com.dallim.auth.authRoutes
import com.dallim.common.ApiResponse
import com.dallim.dallimbook.dallimbookRoutes
import com.dallim.discovery.discoveryRoutes
import com.dallim.meetup.meetupRoutes
import com.dallim.notification.notificationRoutes
import com.dallim.push.deviceTokenRoutes
import com.dallim.route.homeRoutes
import com.dallim.route.routeRoutes
import com.dallim.run.runRoutes
import com.dallim.user.savedRouteRoutes
import com.dallim.user.userRoutes
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/**
 * Root routing tree — every domain in docs/02-api-spec.md is mounted.
 *
 * All API domains are nested under /v1 to match the Base URL declared in docs/02-api-spec.md
 * ("https://api.dallim.app/v1") and android/core-network's BASE_URL build config, which already
 * calls every endpoint with a /v1/ prefix. /health is intentionally left outside /v1 — it's an
 * ops/liveness endpoint, not part of the versioned API surface.
 */
fun Application.configureRouting() {
    routing {
        get("/health") {
            call.respond(ApiResponse.success(mapOf("status" to "ok")))
        }
        route("/v1") {
            authRoutes()
            userRoutes()
            routeRoutes()
            savedRouteRoutes()
            homeRoutes()
            runRoutes()
            dallimbookRoutes()
            discoveryRoutes()
            notificationRoutes()
            deviceTokenRoutes()
            meetupRoutes()
        }
    }
}
