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
    // discovery daily quota (v1.3 문서 S-12/13, docs/02-api-spec.md 8.4)
    const val DISCOVERY_QUOTA_EXCEEDED = "DISCOVERY_QUOTA_EXCEEDED"

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

    // race calendar (docs/02-api-spec.md 16장, com.dallim.race — 완주 이력과 무관한 별개 도메인)
    const val RACE_NOT_FOUND = "RACE_NOT_FOUND"

    // social session (docs/02-api-spec.md 17장, com.dallim.social — S-30~S-34, 1단계)
    const val SESSION_NOT_FOUND = "SESSION_NOT_FOUND"
    const val SESSION_NOT_HOST = "SESSION_NOT_HOST"
    const val SESSION_HOST_REQUIRES_FIRST_RUN = "SESSION_HOST_REQUIRES_FIRST_RUN"
    const val SESSION_CANCELLED = "SESSION_CANCELLED"
    // 성별/온도/기타 어떤 참가 조건이든 전부 이 코드 하나 + 동일 문구로 통일한다 — 코드나 문구를
    // 세분화하면 "내 조건만 사유가 없네" 식의 성별 역추론이 가능해진다 (S-32 SPEC 핵심 요구사항).
    const val SESSION_CONDITION_NOT_MET = "SESSION_CONDITION_NOT_MET"
    const val SESSION_ALREADY_APPLIED = "SESSION_ALREADY_APPLIED"
    const val SESSION_FULL = "SESSION_FULL"
    const val SESSION_APPLICATION_NOT_FOUND = "SESSION_APPLICATION_NOT_FOUND"
    const val SESSION_APPLICATION_NOT_PENDING = "SESSION_APPLICATION_NOT_PENDING"
    const val SESSION_APPLICANT_NOT_FOUND = "SESSION_APPLICANT_NOT_FOUND"
    // 신청 마감(2026-09-16 사용자 지시) — 일정 3일 전이 지나면 신규 신청을 막는다.
    const val SESSION_APPLY_WINDOW_CLOSED = "SESSION_APPLY_WINDOW_CLOSED"
    // 호스트 응답 5시간 제한(2026-09-16 사용자 지시) — approve() 시점에 방금 EXPIRED로 전환된
    // 신청을 SESSION_APPLICATION_NOT_PENDING보다 더 명확히 알리기 위한 전용 코드(다른
    // 비-PENDING 사유는 여전히 SESSION_APPLICATION_NOT_PENDING).
    const val SESSION_APPLICATION_EXPIRED = "SESSION_APPLICATION_EXPIRED"

    // social session 2단계 (docs/02-api-spec.md 17장 이어서 -- S-35~S-39, 2026-09-13)
    // 채팅(S-35)/체크인(S-36)/Ready Check(S-37)/평가(S-38) 전부 "호스트도 아니고 승인된 참가자도
    // 아님"을 이 코드 하나로 통일한다(세분화할 이유가 없음 -- 성별 조건처럼 사유를 숨겨야 하는
    // 케이스가 아니라 그냥 재사용).
    const val SESSION_NOT_PARTICIPANT = "SESSION_NOT_PARTICIPANT"
    const val SESSION_CHAT_ACCESS_EXPIRED = "SESSION_CHAT_ACCESS_EXPIRED"
    const val CHAT_MESSAGE_NOT_FOUND = "CHAT_MESSAGE_NOT_FOUND"
    const val SESSION_CHECKIN_OUTSIDE_WINDOW = "SESSION_CHECKIN_OUTSIDE_WINDOW"
    const val SESSION_CHECKIN_TOO_FAR = "SESSION_CHECKIN_TOO_FAR"
    const val SESSION_ALREADY_STARTED = "SESSION_ALREADY_STARTED"
    const val SESSION_FEEDBACK_NOT_ELIGIBLE = "SESSION_FEEDBACK_NOT_ELIGIBLE"
    const val SESSION_FEEDBACK_TARGET_NOT_ELIGIBLE = "SESSION_FEEDBACK_TARGET_NOT_ELIGIBLE"

    // user block (docs/02-api-spec.md 18장, com.dallim.user.BlockedUser — 채팅 발신자 차단만,
    // 세션 신청/매칭 파급 효과 없음). 자기 자신 차단은 별도 코드 없이 기존 VALIDATION_ERROR를
    // 그대로 쓴다(사용자 지시).
    const val BLOCK_TARGET_NOT_FOUND = "BLOCK_TARGET_NOT_FOUND"

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
