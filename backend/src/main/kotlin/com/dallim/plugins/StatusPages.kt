package com.dallim.plugins

import com.dallim.common.ApiResponse
import com.dallim.common.ApiException
import com.dallim.common.ErrorCodes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.dallim.plugins.StatusPages")

/**
 * Single place every thrown exception is translated into the ApiResponse.error envelope
 * (docs/02-api-spec.md 0장). Domain code should throw ApiException (or a subclass —
 * see com.dallim.common.ApiException) with one of the ErrorCodes constants; anything else
 * bubbling up here is logged and reported as a generic 500 INTERNAL_ERROR so we never leak
 * stack traces or internal messages to clients.
 *
 * Never log request/response bodies here for auth routes (passwords, tokens) — only
 * method+path+exception type, per CLAUDE.md rule 5.
 */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ApiResponse.error<Unit>(cause.code, cause.message))
        }

        exception<BadRequestException> { call, cause ->
            logger.warn("Malformed request: ${call.request.uri} (${cause::class.simpleName})")
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse.error<Unit>(ErrorCodes.VALIDATION_ERROR, "요청 형식이 올바르지 않습니다."),
            )
        }

        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception on ${call.request.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiResponse.error<Unit>(ErrorCodes.INTERNAL_ERROR, "일시적인 오류가 발생했습니다."),
            )
        }

        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ApiResponse.error<Unit>("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."))
        }
    }
}
