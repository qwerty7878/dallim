package com.dallim.trainingplan

import com.dallim.common.ApiResponse
import com.dallim.plugins.AUTH_JWT
import com.dallim.plugins.currentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * 대회 목표 훈련 플랜(S-86) 엔드포인트 — POST/GET /races/{raceId}/training-plan
 * (docs/02-api-spec.md 19장). 둘 다 로그인 필요 — "내 목표 대회"라는 개념 자체가 유저별이라
 * com.dallim.race.raceRoutes의 조회 엔드포인트들과 달리 optional 인증을 쓰지 않는다.
 */
fun Route.trainingPlanRoutes() {
    val trainingPlanService by inject<TrainingPlanService>()

    authenticate(AUTH_JWT) {
        route("/races/{raceId}/training-plan") {
            post {
                val raceId = call.parameters["raceId"]!!
                val userId = call.currentUserId()!!
                val request = call.receive<TrainingPlanGenerateRequest>()
                val response = trainingPlanService.requestGeneration(userId, raceId, request.category)
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }

            get {
                val raceId = call.parameters["raceId"]!!
                val userId = call.currentUserId()!!
                val response = trainingPlanService.getPlan(userId, raceId)
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }
        }
    }
}
