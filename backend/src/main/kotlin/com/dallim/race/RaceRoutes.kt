package com.dallim.race

import com.dallim.common.ApiResponse
import com.dallim.plugins.AUTH_JWT
import com.dallim.plugins.currentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * 대회 캘린더 엔드포인트 — GET /races, GET /races/{raceId}, POST/DELETE /races/{raceId}/save,
 * GET /users/me/races (docs/02-api-spec.md 16장). GET /races(목록)/{raceId}(상세)는
 * com.dallim.route.RouteRoutes와 동일하게 비로그인으로도 조회 가능하되 `isSaved`만
 * `authenticate(AUTH_JWT, optional = true)`로 개인화한다.
 */
fun Route.raceRoutes() {
    val raceService by inject<RaceService>()

    route("/races") {
        authenticate(AUTH_JWT, optional = true) {
            get {
                val q = call.request.queryParameters
                val userId = call.currentUserId()
                val response = raceService.listRaces(
                    region = q["region"],
                    category = q["category"],
                    status = q["status"],
                    page = q["page"]?.toIntOrNull() ?: 0,
                    size = q["size"]?.toIntOrNull() ?: 20,
                    userId = userId,
                )
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }

            get("/{raceId}") {
                val raceId = call.parameters["raceId"]!!
                val userId = call.currentUserId()
                val response = raceService.getDetail(raceId, userId)
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }
        }

        authenticate(AUTH_JWT) {
            post("/{raceId}/save") {
                val raceId = call.parameters["raceId"]!!
                val userId = call.currentUserId()!!
                raceService.save(userId, raceId)
                call.respond(HttpStatusCode.Created, ApiResponse.success<Unit>(null))
            }

            delete("/{raceId}/save") {
                val raceId = call.parameters["raceId"]!!
                val userId = call.currentUserId()!!
                raceService.unsave(userId, raceId)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }
        }
    }

    authenticate(AUTH_JWT) {
        get("/users/me/races") {
            val userId = call.currentUserId()!!
            val response = raceService.listMyRaces(userId)
            call.respond(HttpStatusCode.OK, ApiResponse.success(response))
        }
    }
}
