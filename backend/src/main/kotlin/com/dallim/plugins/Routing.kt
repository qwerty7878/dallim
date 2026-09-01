package com.dallim.plugins

import com.dallim.auth.authRoutes
import com.dallim.common.ApiResponse
import com.dallim.dallimbook.dallimbookRoutes
import com.dallim.route.homeRoutes
import com.dallim.route.routeRoutes
import com.dallim.run.runRoutes
import com.dallim.user.savedRouteRoutes
import com.dallim.user.userRoutes
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * Root routing tree — every domain in docs/02-api-spec.md is mounted.
 */
fun Application.configureRouting() {
    routing {
        get("/health") {
            call.respond(ApiResponse.success(mapOf("status" to "ok")))
        }
        authRoutes()
        userRoutes()
        routeRoutes()
        savedRouteRoutes()
        homeRoutes()
        runRoutes()
        dallimbookRoutes()
    }
}
