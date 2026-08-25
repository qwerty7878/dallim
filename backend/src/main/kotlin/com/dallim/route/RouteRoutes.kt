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
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Route endpoints — GET /routes, /routes/{routeId}, /routes/{routeId}/finishers
 * (docs/02-api-spec.md 4장). Handlers are thin: parse the request, delegate to RouteService,
 * wrap in ApiResponse. StatusPages translates any thrown ApiException (e.g. 404 ROUTE_NOT_FOUND)
 * into the common error envelope.
 *
 * GET /routes/{routeId} is usable anonymously but personalizes `isSaved` when a valid Bearer
 * token is present, via `authenticate(AUTH_JWT, optional = true)`. GET /routes itself is fully
 * public — its response schema has no per-item isSaved field (see RouteDtos.kt doc comment).
 */
fun Route.routeRoutes() {
    val routeService by inject<RouteService>()

    route("/routes") {
        get {
            val q = call.request.queryParameters
            val response = routeService.listRoutes(
                lat = q["lat"]?.toDoubleOrNull(),
                lng = q["lng"]?.toDoubleOrNull(),
                radiusKm = q["radiusKm"]?.toDoubleOrNull() ?: 5.0,
                minDistanceKm = q["minDistanceKm"]?.toDoubleOrNull(),
                maxDistanceKm = q["maxDistanceKm"]?.toDoubleOrNull(),
                status = q["status"]?.let { raw -> runCatching { RouteStatus.valueOf(raw) }.getOrNull() },
                sort = q["sort"] ?: "popular",
                page = q["page"]?.toIntOrNull() ?: 0,
                size = q["size"]?.toIntOrNull() ?: 20,
            )
            call.respond(HttpStatusCode.OK, ApiResponse.success(response))
        }

        get("/{routeId}/finishers") {
            val routeId = call.parameters["routeId"]!!
            val response = routeService.getFinishers(routeId)
            call.respond(HttpStatusCode.OK, ApiResponse.success(response))
        }

        authenticate(AUTH_JWT, optional = true) {
            get("/{routeId}") {
                val routeId = call.parameters["routeId"]!!
                val userId = call.currentUserId()
                val response = routeService.getDetail(routeId, userId)
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }
        }
    }
}
