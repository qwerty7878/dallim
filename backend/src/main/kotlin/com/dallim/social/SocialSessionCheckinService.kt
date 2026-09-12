package com.dallim.social

import com.dallim.common.BadRequestException
import com.dallim.common.ConflictException
import com.dallim.common.ErrorCodes
import com.dallim.common.ForbiddenException
import com.dallim.common.GeoMath
import com.dallim.common.LatLng
import com.dallim.common.NotFoundException
import java.time.Instant

/**
 * S-36 GPS 체크인 / S-37 Ready Check 비즈니스 로직 — docs/02-api-spec.md 17장 이어서.
 * SocialSessionCheckinRoutes.kt는 얇은 HTTP 어댑터로 남기고, 윈도우/반경 판정 + 상태 전이는
 * 전부 여기 둔다(com.dallim.social.SocialSessionService와 동일 원칙).
 */
class SocialSessionCheckinService(
    private val sessionRepository: SocialSessionRepository,
    private val checkinRepository: SocialSessionCheckinRepository,
) {

    /** POST /social-sessions/{id}/checkin — S-36. 이미 CHECKED_IN/LATE면 재검증 없이 기존
     * 결과를 그대로 반환한다(멱등 -- 재전송/중복 탭 대비). */
    fun checkin(sessionId: String, userId: String, lat: Double, lng: Double): CheckinResponse {
        val session = findSessionOr404(sessionId)
        requireHostOrApprovedParticipant(session, userId)
        if (session.startedAt != null) {
            throw BadRequestException(ErrorCodes.SESSION_ALREADY_STARTED, "이미 시작된 세션이에요.")
        }

        val distance = GeoMath.haversineMeters(
            LatLng(session.meetingPointLat, session.meetingPointLng),
            LatLng(lat, lng),
        )

        val existing = checkinRepository.find(sessionId, userId)
        if (existing != null &&
            (existing.status == SocialSessionCheckinStatus.CHECKED_IN || existing.status == SocialSessionCheckinStatus.LATE)
        ) {
            return CheckinResponse(status = existing.status, distanceToMeetingPointM = distance, checkedInAt = existing.checkedInAt?.toString())
        }

        val now = Instant.now()
        if (!SocialSessionCheckinWindow.isWithinWindow(session.scheduledAt, now)) {
            throw BadRequestException(ErrorCodes.SESSION_CHECKIN_OUTSIDE_WINDOW, "지금은 체크인할 수 있는 시간이 아니에요.")
        }
        if (!SocialSessionCheckinWindow.isWithinRadius(distance)) {
            throw BadRequestException(ErrorCodes.SESSION_CHECKIN_TOO_FAR, "집결지에서 너무 멀어요.")
        }

        val status = SocialSessionCheckinWindow.resolveStatus(session.scheduledAt, now)
        checkinRepository.upsertSelfCheckin(sessionId, userId, status, now, distance)
        return CheckinResponse(status = status, distanceToMeetingPointM = distance, checkedInAt = now.toString())
    }

    /** GET /social-sessions/{id}/ready-check — S-37, 호스트+APPROVED 참가자만. */
    fun readyCheck(sessionId: String, callerUserId: String): ReadyCheckResponse {
        val session = findSessionOr404(sessionId)
        requireHostOrApprovedParticipant(session, callerUserId)

        val participantUserIds = participantUserIds(session)
        val rows = checkinRepository.findAllForSession(sessionId, participantUserIds)
        return ReadyCheckResponse(
            items = rows.map {
                ReadyCheckItem(
                    userId = it.userId,
                    nickname = it.nickname,
                    avatarId = it.avatarId,
                    isHost = it.userId == session.hostUserId,
                    status = it.status,
                    checkedInAt = it.checkedInAt?.toString(),
                    distanceErrorM = it.distanceErrorM,
                    manualByHost = it.manualByHost,
                )
            },
            started = session.startedAt != null,
        )
    }

    /** POST /social-sessions/{id}/start — S-37, 호스트만. 미체크인 인원이 있어도 항상 시작
     * 가능하다(대기 강제 금지) -- 대신 APPROVED 참가자 중 아직 CHECKED_IN/LATE가 아닌 사람은
     * 전부 NO_SHOW로 일괄 전환한다. 호스트 자신은 이 스윕 대상이 아니다(시작을 누른 사람은 항상
     * 참석 중이라고 본다). 중복 시작은 409. */
    fun start(sessionId: String, callerUserId: String) {
        val session = findSessionOr404(sessionId)
        requireHost(session, callerUserId)

        val approvedApplicantIds = sessionRepository.findApprovedApplicants(sessionId).map { it.userId }
        checkinRepository.markNoShowIfWaiting(sessionId, approvedApplicantIds)

        val started = sessionRepository.markStarted(sessionId, Instant.now())
        if (!started) {
            throw ConflictException(ErrorCodes.SESSION_ALREADY_STARTED, "이미 시작된 세션이에요.")
        }
    }

    /** POST /social-sessions/{id}/checkins/{userId}/manual-confirm — S-36 예외 처리, 호스트만.
     * GPS 오차/실내 집결 대비 + NO_SHOW 오판정 이의제기 해결 경로를 이 엔드포인트 하나로
     * 커버한다(별도 이의제기 API 없음 -- 과설계 금지). */
    fun manualConfirm(sessionId: String, callerUserId: String, targetUserId: String) {
        val session = findSessionOr404(sessionId)
        requireHost(session, callerUserId)

        val isTargetEligible = targetUserId == session.hostUserId ||
            sessionRepository.findApplicant(sessionId, targetUserId)?.status == SocialSessionApplicantStatus.APPROVED
        if (!isTargetEligible) {
            throw NotFoundException(ErrorCodes.SESSION_APPLICANT_NOT_FOUND, "참가자를 찾을 수 없습니다.")
        }
        checkinRepository.manualConfirm(sessionId, targetUserId, Instant.now())
    }

    private fun participantUserIds(session: SocialSessionRepository.SessionRow): List<String> =
        listOf(session.hostUserId) + sessionRepository.findApprovedApplicants(session.id).map { it.userId }

    private fun requireHostOrApprovedParticipant(session: SocialSessionRepository.SessionRow, userId: String) {
        val isApproved = sessionRepository.findApplicant(session.id, userId)?.status == SocialSessionApplicantStatus.APPROVED
        if (session.hostUserId != userId && !isApproved) {
            throw ForbiddenException(ErrorCodes.SESSION_NOT_PARTICIPANT, "이 세션의 참가자만 할 수 있어요.")
        }
    }

    private fun requireHost(session: SocialSessionRepository.SessionRow, callerUserId: String) {
        if (session.hostUserId != callerUserId) {
            throw ForbiddenException(ErrorCodes.SESSION_NOT_HOST, "호스트만 할 수 있어요.")
        }
    }

    private fun findSessionOr404(sessionId: String): SocialSessionRepository.SessionRow =
        sessionRepository.findById(sessionId) ?: throw NotFoundException(ErrorCodes.SESSION_NOT_FOUND, "세션을 찾을 수 없습니다.")
}
