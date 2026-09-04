package com.dallim.notification

import com.dallim.common.ApiResponse
import com.dallim.plugins.AUTH_JWT
import com.dallim.plugins.currentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Notification endpoints — GET /notifications, GET /notifications/unread-count,
 * POST /notifications/{id}/read (docs/02-api-spec.md 9장, S-46). All 🔒 (JWT required).
 *
 * Notification *creation* has no client-facing route in this phase — see
 * NotificationService.notifyRunCompleted, called from com.dallim.run.RunService.finishRun (9.4).
 */
fun Route.notificationRoutes() {
    val notificationService by inject<NotificationService>()

    authenticate(AUTH_JWT) {
        route("/notifications") {
            get {
                val userId = call.currentUserId()!!
                val q = call.request.queryParameters
                val response = notificationService.listMyNotifications(
                    userId = userId,
                    page = q["page"]?.toIntOrNull() ?: 0,
                    size = q["size"]?.toIntOrNull() ?: 20,
                )
                call.respond(HttpStatusCode.OK, ApiResponse.success(response))
            }

            get("/unread-count") {
                val userId = call.currentUserId()!!
                call.respond(HttpStatusCode.OK, ApiResponse.success(notificationService.unreadCount(userId)))
            }

            post("/{id}/read") {
                val userId = call.currentUserId()!!
                val notificationId = call.parameters["id"]!!
                notificationService.markRead(userId, notificationId)
                call.respond(HttpStatusCode.OK, ApiResponse.success<Unit?>(null))
            }
        }
    }
}
