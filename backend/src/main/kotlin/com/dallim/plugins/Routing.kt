package com.dallim.plugins

import com.dallim.auth.authRoutes
import com.dallim.common.ApiResponse
import com.dallim.dallimbook.dallimbookRoutes
import com.dallim.route.homeRoutes
import com.dallim.route.routeRoutes
import com.dallim.run.runRoutes
import com.dallim.user.savedRouteRoutes
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * Root routing tree. auth/route/saved-routes/home/run/dallimbook are mounted; the rest of the
 * user domain (profile/nickname-check) is still backend-dev's job for a later round and should be
 * mounted as a `Route.xxxRoutes()` extension function the same way.
 */
fun Application.configureRouting() {
    routing {
        get("/health") {
            call.respond(ApiResponse.success(mapOf("status" to "ok")))
        }
        authRoutes()
        routeRoutes()
        savedRouteRoutes()
        homeRoutes()
        runRoutes()
        dallimbookRoutes()
    }
}
