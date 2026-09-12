package com.dallim.social

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
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/** S-39 Running Mate — docs/02-api-spec.md 17장 이어서. 도메인은 com.dallim.social이지만
 * 리소스 경로는 "내 소유물"이라 /users/me 하위에 마운트한다(com.dallim.user.savedRouteRoutes가
 * /users/me/saved-routes를 쓰는 것과 동일 관례). */
fun Route.runningMateRoutes() {
    val runningMateService by inject<RunningMateService>()

    authenticate(AUTH_JWT) {
        route("/users/me/running-mates") {
            get {
                val userId = call.currentUserId()!!
                call.respond(HttpStatusCode.OK, ApiResponse.success(runningMateService.listMine(userId)))
            }

            delete("/{mateUserId}") {
                val userId = call.currentUserId()!!
                val mateUserId = call.parameters["mateUserId"]!!
                runningMateService.hide(userId, mateUserId)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }
        }
    }
}
