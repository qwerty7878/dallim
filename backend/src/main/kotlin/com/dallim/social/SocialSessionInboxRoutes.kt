package com.dallim.social

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
 * GET /users/me/social-sessions — 채팅 인박스(18장). "내 소유물" 리소스라 /users/me 하위에
 * 마운트한다(com.dallim.social.runningMateRoutes/com.dallim.user.savedRouteRoutes와 동일 관례).
 * 안드로이드가 이 목록을 바텀 네비게이션 "채팅" 탭에 쓸 예정 -- 화면 자체는 이번 라운드 범위 밖,
 * API만 먼저 낸다.
 */
fun Route.socialSessionInboxRoutes() {
    val sessionService by inject<SocialSessionService>()

    authenticate(AUTH_JWT) {
        route("/users/me/social-sessions") {
            get {
                val userId = call.currentUserId()!!
                call.respond(HttpStatusCode.OK, ApiResponse.success(sessionService.listMine(userId)))
            }
        }
    }
}
