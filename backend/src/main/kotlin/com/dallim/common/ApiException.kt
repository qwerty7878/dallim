package com.dallim.common

import io.ktor.http.HttpStatusCode

/**
 * Base type for every business/domain error thrown by a route handler or service.
 * Caught centrally by the StatusPages plugin (see plugins/StatusPages.kt) and
 * rendered as ApiResponse.error(code, message) with [status].
 *
 * Domain modules should define their own error codes as constants (see below)
 * and throw `ApiException(HttpStatusCode.Conflict, ErrorCodes.EMAIL_ALREADY_EXISTS, "...")`
 * rather than inventing ad-hoc strings — keep the code<->message pairs discoverable
 * in one place per docs/02-api-spec.md.
 */
open class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message)

/**
 * Central registry of error codes referenced across docs/02-api-spec.md.
 * backend-dev should throw ApiException with these constants instead of raw strings
 * so the code<->message mapping stays consistent across modules.
 */
object ErrorCodes {
    // auth
    const val INVALID_GOOGLE_TOKEN = "INVALID_GOOGLE_TOKEN"
    const val INVALID_KAKAO_TOKEN = "INVALID_KAKAO_TOKEN"
    const val EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS"
    const val INVALID_PASSWORD_FORMAT = "INVALID_PASSWORD_FORMAT"
    const val INVALID_EMAIL_FORMAT = "INVALID_EMAIL_FORMAT"
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val ACCOUNT_EXISTS_DIFFERENT_PROVIDER = "ACCOUNT_EXISTS_DIFFERENT_PROVIDER"
    const val REFRESH_TOKEN_EXPIRED_OR_INVALID = "REFRESH_TOKEN_EXPIRED_OR_INVALID"

    // user
    const val NICKNAME_TAKEN = "NICKNAME_TAKEN"

    // route
    const val ROUTE_NOT_FOUND = "ROUTE_NOT_FOUND"

    // run
    const val RUN_ALREADY_FINISHED = "RUN_ALREADY_FINISHED"
    const val GPS_DATA_INSUFFICIENT = "GPS_DATA_INSUFFICIENT"
    const val RUN_NOT_FOUND = "RUN_NOT_FOUND"

    // discovery (docs/02-api-spec.md 8장)
    const val DRAW_TOO_SHORT = "DRAW_TOO_SHORT"
    const val DRAW_MATCH_FAILED = "DRAW_MATCH_FAILED"
    const val DISCOVERY_NO_ROUTE = "DISCOVERY_NO_ROUTE"

    // notification (docs/02-api-spec.md 9장)
    const val NOTIFICATION_NOT_FOUND = "NOTIFICATION_NOT_FOUND"

    // meetup (docs/02-api-spec.md 14장, run-together recruiting)
    const val MEETUP_NOT_FOUND = "MEETUP_NOT_FOUND"
    const val ALREADY_JOINED = "ALREADY_JOINED"
    const val MEETUP_FULL = "MEETUP_FULL"
    const val MEETUP_ENDED = "MEETUP_ENDED"
    const val MEETUP_NOT_HOST = "MEETUP_NOT_HOST"

    // race record (docs/02-api-spec.md 15장, 러닝 커리어/완주 이력)
    const val RACE_RECORD_NOT_FOUND = "RACE_RECORD_NOT_FOUND"

    // generic
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
}

class NotFoundException(code: String, message: String) :
    ApiException(HttpStatusCode.NotFound, code, message)

class ConflictException(code: String, message: String) :
    ApiException(HttpStatusCode.Conflict, code, message)

/** 403 — the caller is authenticated but not allowed to perform this action (first use:
 * DELETE /meetups/{meetupId} by a non-host, docs/02-api-spec.md 14.4 MEETUP_NOT_HOST). */
class ForbiddenException(code: String, message: String) :
    ApiException(HttpStatusCode.Forbidden, code, message)

class BadRequestException(code: String, message: String) :
    ApiException(HttpStatusCode.BadRequest, code, message)

class UnauthorizedException(code: String = ErrorCodes.UNAUTHORIZED, message: String = "인증이 필요합니다.") :
    ApiException(HttpStatusCode.Unauthorized, code, message)

/** 422 — request was well-formed but semantically impossible to fulfill (e.g. DISCOVERY_NO_ROUTE). */
class UnprocessableEntityException(code: String, message: String) :
    ApiException(HttpStatusCode.UnprocessableEntity, code, message)
