package com.dallim.racerecord

import com.dallim.common.ApiResponse
import com.dallim.plugins.AUTH_JWT
import com.dallim.plugins.currentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * 완주 이력(러닝 커리어) 엔드포인트 — docs/02-api-spec.md 15장. 전부 🔒(JWT 필요), 본인
 * 이력만 조회/수정/삭제 가능(RaceRecordService가 강제).
 */
fun Route.raceRecordRoutes() {
    val raceRecordService by inject<RaceRecordService>()

    authenticate(AUTH_JWT) {
        route("/users/me/race-records") {
            get {
                val userId = call.currentUserId()!!
                call.respond(HttpStatusCode.OK, ApiResponse.success(raceRecordService.list(userId)))
            }

            post {
                val userId = call.currentUserId()!!
                val request = call.receive<CreateRaceRecordRequest>()
                val response = raceRecordService.create(userId, request)
                call.respond(HttpStatusCode.Created, ApiResponse.success(response))
            }

            // 정적 경로가 "/{id}" 파라미터 경로보다 먼저 매칭되므로 순서와 무관하게 안전하다.
            post("/pace-suggestion") {
                val request = call.receive<PaceSuggestionRequest>()
                call.respond(HttpStatusCode.OK, ApiResponse.success(raceRecordService.suggestPace(request)))
            }

            patch("/{id}") {
                val userId = call.currentUserId()!!
                val id = call.parameters["id"]!!
                val request = call.receive<UpdateRaceRecordRequest>()
                val response = raceRecordService.update(userId, id, request)
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }

            delete("/{id}") {
                val userId = call.currentUserId()!!
                val id = call.parameters["id"]!!
                raceRecordService.delete(userId, id)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }
        }
    }
}
