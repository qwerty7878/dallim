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
 * Route endpoints — GET /routes, /routes/{routeId}, /routes/{routeId}/finishers,
 * /routes/places/search (docs/02-api-spec.md 4장, 12장). Handlers are thin: parse the request,
 * delegate to RouteService/PlaceSearchService, wrap in ApiResponse. StatusPages translates any
 * thrown ApiException (e.g. 404 ROUTE_NOT_FOUND, 400 VALIDATION_ERROR) into the common error
 * envelope.
 *
 * Both GET /routes (list) and GET /routes/{routeId} (detail) are usable anonymously but
 * personalize `isSaved` when a valid Bearer token is present, via
 * `authenticate(AUTH_JWT, optional = true)`.
 */
fun Route.routeRoutes() {
    val routeService by inject<RouteService>()
    val placeSearchService by inject<PlaceSearchService>()

    route("/routes") {
        get("/{routeId}/finishers") {
            val routeId = call.parameters["routeId"]!!
            val response = routeService.getFinishers(routeId)
            call.respond(HttpStatusCode.OK, ApiResponse.success(response))
        }

        // docs/02-api-spec.md 12.1 — no 🔒 marker, usable anonymously like the other unauthenticated
        // /routes endpoints above. "places" is a literal path segment so it never collides with
        // /routes/{routeId} below (Ktor prefers a literal match over a parameterized one anyway).
        get("/places/search") {
            val q = call.request.queryParameters
            val response = placeSearchService.search(
                query = q["query"],
                lat = q["lat"]?.toDoubleOrNull(),
                lng = q["lng"]?.toDoubleOrNull(),
            )
            call.respond(HttpStatusCode.OK, ApiResponse.success(response))
        }

        authenticate(AUTH_JWT, optional = true) {
            get {
                val q = call.request.queryParameters
                val userId = call.currentUserId()
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
                    userId = userId,
                )
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }

            get("/{routeId}") {
                val routeId = call.parameters["routeId"]!!
                val userId = call.currentUserId()
                val response = routeService.getDetail(routeId, userId)
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }
        }
    }
}
