package com.dallim.route

import com.dallim.common.ApiResponse
import com.dallim.plugins.AUTH_JWT
import com.dallim.plugins.currentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

/** GET /home (🔒) — docs/02-api-spec.md 3장. */
fun Route.homeRoutes() {
    val homeService by inject<HomeService>()

    authenticate(AUTH_JWT) {
        get("/home") {
            val q = call.request.queryParameters
            val response = homeService.getHome(
                lat = q["lat"]?.toDoubleOrNull(),
                lng = q["lng"]?.toDoubleOrNull(),
                userId = call.currentUserId()!!,
            )
            call.respond(HttpStatusCode.OK, ApiResponse.success(response))
        }
    }
}
