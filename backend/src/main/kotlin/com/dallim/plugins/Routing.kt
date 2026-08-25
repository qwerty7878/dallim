package com.dallim.plugins

import com.dallim.auth.authRoutes
import com.dallim.common.ApiResponse
import com.dallim.route.homeRoutes
import com.dallim.route.routeRoutes
import com.dallim.user.savedRouteRoutes
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * Root routing tree. auth/route/saved-routes/home are mounted; run (and the rest of the user
 * domain — profile/nickname-check) are still backend-dev's job for later rounds and should be
 * mounted as `Route.xxxRoutes()` extension functions the same way, e.g.:
 *
 *   routing {
 *       authRoutes()
 *       routeRoutes()
 *       savedRouteRoutes()
 *       homeRoutes()
 *       runRoutes()
 *   }
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
    }
}
