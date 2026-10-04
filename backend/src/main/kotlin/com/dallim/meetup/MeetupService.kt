package com.dallim.meetup

import com.dallim.common.BadRequestException
import com.dallim.common.ConflictException
import com.dallim.common.ErrorCodes
import com.dallim.common.ForbiddenException
import com.dallim.common.NotFoundException
import com.dallim.notification.NotificationService
import com.dallim.route.RouteRepository
import java.time.Instant

/**
 * Run-together recruiting business logic — docs/02-api-spec.md 14장, docs/01-feature-spec.md 1.8.
 * MeetupRoutes.kt stays a thin HTTP adapter; all validation/state-transition rules live here.
 *
 * "마감"(isFull)/"종료"(isPast) are computed here on every read, never persisted — see
 * com.dallim.meetup.Meetup's doc comment.
 */
class MeetupService(
    private val meetupRepository: MeetupRepository,
    private val routeRepository: RouteRepository,
    private val notificationService: NotificationService,
) {

    /** POST /routes/{routeId}/meetups — 14.3. */
    fun create(routeId: String, hostUserId: String, request: CreateMeetupRequest): CreateMeetupResponse {
        if (!routeRepository.exists(routeId)) {
            throw NotFoundException(ErrorCodes.ROUTE_NOT_FOUND, "코스를 찾을 수 없습니다.")
        }

        val scheduledAt = parseInstant(request.scheduledAt)
        if (scheduledAt.isBefore(Instant.now())) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "모집 일정은 현재 시각 이후여야 합니다.")
        }
        if (request.maxParticipants !in 2..20) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "정원은 2명 이상 20명 이하여야 합니다.")
        }

        val meetupId = meetupRepository.create(
            routeId = routeId,
            hostUserId = hostUserId,
            scheduledAt = scheduledAt,
            maxParticipants = request.maxParticipants,
            description = request.description,
        )
        return CreateMeetupResponse(meetupId = meetupId)
    }

    /**
     * GET /routes/{routeId}/meetups — 14.2. isFull/isPast는 서버가 계산한다.
     *
     * 정렬(2026-10-05 변경): 예전에는 `scheduledAt` 오름차순 하나뿐이라 **이미 끝난 모집이 목록 맨 위**에
     * 올라왔다(가장 오래된 것이 제일 앞). 지금 참가할 수 있는 모집을 먼저 보여주도록
     * "아직 안 지난 것 먼저(임박순) → 지난 것은 뒤로"로 바꿨다.
     * 대회 목록(com.dallim.race.RaceService.listRaces)의 "마감 안 된 것 먼저"와 같은 원칙이다.
     *
     * 모집이 적은 화면(코스 1개에 달린 모집)이라 페이지네이션이 없어 Kotlin에서 정렬한다 —
     * 페이지네이션이 있는 소셜 세션 목록은 SQL에서 같은 순서를 만든다
     * (com.dallim.social.SocialSessionRepository.list).
     */
    fun listByRoute(routeId: String): MeetupListResponse {
        if (!routeRepository.exists(routeId)) {
            throw NotFoundException(ErrorCodes.ROUTE_NOT_FOUND, "코스를 찾을 수 없습니다.")
        }

        val rows = meetupRepository.findByRoute(routeId)
        val counts = meetupRepository.countParticipantsByMeetup(rows.map { it.id })
        val now = Instant.now()

        val items = rows
            .sortedWith(compareBy({ it.scheduledAt.isBefore(now) }, { it.scheduledAt }))
            .map { row ->
                val current = counts[row.id] ?: 0
                MeetupListItem(
                    meetupId = row.id,
                    hostNickname = row.hostNickname,
                    scheduledAt = row.scheduledAt.toString(),
                    maxParticipants = row.maxParticipants,
                    currentParticipants = current,
                    status = row.status,
                    isFull = current >= row.maxParticipants,
                    isPast = row.scheduledAt.isBefore(now),
                )
            }
        return MeetupListResponse(items = items)
    }

    /** GET /meetups/{meetupId} — 14.4. */
    fun getDetail(meetupId: String, userId: String): MeetupDetailResponse {
        val row = findMeetupOr404(meetupId)
        val current = meetupRepository.countParticipants(meetupId)
        val now = Instant.now()
        val participants = meetupRepository.findParticipants(meetupId)
        val routeName = meetupRepository.findRouteName(row.routeId) ?: ""

        return MeetupDetailResponse(
            meetupId = row.id,
            routeId = row.routeId,
            routeName = routeName,
            hostUserId = row.hostUserId,
            hostNickname = row.hostNickname,
            scheduledAt = row.scheduledAt.toString(),
            maxParticipants = row.maxParticipants,
            description = row.description,
            status = row.status,
            isFull = current >= row.maxParticipants,
            isPast = row.scheduledAt.isBefore(now),
            isHost = row.hostUserId == userId,
            isJoined = meetupRepository.isParticipant(meetupId, userId),
            participants = participants.map {
                MeetupParticipantItem(userId = it.userId, nickname = it.nickname, isHost = it.userId == row.hostUserId)
            },
        )
    }

    /** POST /meetups/{meetupId}/join — 14.4. Order of checks: already-joined (identity) before
     * ended/full (availability) — a host or existing participant always gets ALREADY_JOINED
     * regardless of the meetup's own state, since SPEC calls out "host 포함" explicitly. Between
     * the two remaining checks, "ended" (time passed or cancelled) is treated as more terminal
     * than "full" when both are true. */
    fun join(meetupId: String, userId: String) {
        val row = findMeetupOr404(meetupId)

        if (meetupRepository.isParticipant(meetupId, userId)) {
            throw ConflictException(ErrorCodes.ALREADY_JOINED, "이미 참가 중인 모집이에요.")
        }
        if (row.status == MeetupStatus.CANCELLED || row.scheduledAt.isBefore(Instant.now())) {
            throw ConflictException(ErrorCodes.MEETUP_ENDED, "종료된 모집이에요.")
        }
        val current = meetupRepository.countParticipants(meetupId)
        if (current >= row.maxParticipants) {
            throw ConflictException(ErrorCodes.MEETUP_FULL, "정원이 가득 찼어요.")
        }

        meetupRepository.addParticipant(meetupId, userId)

        val joinerNickname = meetupRepository.findParticipants(meetupId)
            .firstOrNull { it.userId == userId }
            ?.nickname
            ?: "달림이"
        val routeName = meetupRepository.findRouteName(row.routeId) ?: ""
        notificationService.notifyMeetupJoined(hostUserId = row.hostUserId, joinerNickname = joinerNickname, routeName = routeName)
    }

    /** POST /meetups/{meetupId}/leave — 14.4. Host can't leave (must DELETE instead); leaving
     * while not joined is a no-op success (idempotent). */
    fun leave(meetupId: String, userId: String) {
        val row = findMeetupOr404(meetupId)

        if (row.hostUserId == userId) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "모집을 취소하려면 삭제를 사용하세요.")
        }
        meetupRepository.removeParticipant(meetupId, userId)
    }

    /** DELETE /meetups/{meetupId} — 14.4. Soft-cancel only, host-only. */
    fun cancel(meetupId: String, userId: String) {
        val row = findMeetupOr404(meetupId)

        if (row.hostUserId != userId) {
            throw ForbiddenException(ErrorCodes.MEETUP_NOT_HOST, "모집 작성자만 취소할 수 있어요.")
        }
        meetupRepository.cancel(meetupId)
    }

    private fun findMeetupOr404(meetupId: String): MeetupRepository.MeetupRow =
        meetupRepository.findById(meetupId) ?: throw NotFoundException(ErrorCodes.MEETUP_NOT_FOUND, "모집을 찾을 수 없습니다.")

    private fun parseInstant(raw: String): Instant = try {
        Instant.parse(raw)
    } catch (e: Exception) {
        throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "시간 형식이 올바르지 않습니다.")
    }
}
