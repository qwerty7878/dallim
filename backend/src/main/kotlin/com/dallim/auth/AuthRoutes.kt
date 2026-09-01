package com.dallim.auth

import com.dallim.common.ApiResponse
import com.dallim.plugins.AUTH_JWT
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Auth endpoints (POST /auth/google, /kakao, /signup, /login, /refresh, /logout) — see
 * docs/02-api-spec.md 1장. Route handlers are thin: parse request, delegate to
 * AuthService, wrap the result in ApiResponse. All error-code decisions live in AuthService;
 * StatusPages (plugins/StatusPages.kt) translates any thrown ApiException into the common
 * error envelope.
 */
fun Route.authRoutes() {
    val authService by inject<AuthService>()

    route("/auth") {
        post("/google") {
            val request = call.receive<GoogleLoginRequest>()
            val result = authService.loginWithGoogle(request.idToken)
            call.respond(HttpStatusCode.OK, ApiResponse.success(result))
        }

        post("/kakao") {
            val request = call.receive<KakaoLoginRequest>()
            val result = authService.loginWithKakao(request.kakaoAccessToken)
            call.respond(HttpStatusCode.OK, ApiResponse.success(result))
        }

        post("/signup") {
            val request = call.receive<SignupRequest>()
            val result = authService.signup(request.email, request.password)
            call.respond(HttpStatusCode.Created, ApiResponse.success(result))
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val result = authService.login(request.email, request.password)
            call.respond(HttpStatusCode.OK, ApiResponse.success(result))
        }

        post("/refresh") {
            val request = call.receive<RefreshRequest>()
            val result = authService.refresh(request.refreshToken)
            call.respond(HttpStatusCode.OK, ApiResponse.success(result))
        }

        authenticate(AUTH_JWT) {
            post("/logout") {
                val request = call.receive<LogoutRequest>()
                authService.logout(request.refreshToken)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit>(null))
            }
        }
    }
}
