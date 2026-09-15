package com.dallim.social

import com.dallim.common.BadRequestException
import com.dallim.common.ConflictException
import com.dallim.common.ErrorCodes
import com.dallim.common.ForbiddenException
import com.dallim.common.NotFoundException
import com.dallim.route.RouteRepository
import com.dallim.user.User
import com.dallim.user.UserRepository
import java.time.Duration
import java.time.Instant

/**
 * 소셜 세션 1단계(S-30~S-34) 비즈니스 로직 — docs/02-api-spec.md 17장. SocialSessionRoutes.kt는
 * 얇은 HTTP 어댑터로 남기고, 검증/상태 전이 규칙은 전부 여기 둔다 (com.dallim.meetup.MeetupService
 * 와 동일 원칙).
 *
 * "성사까지 N명"(NEAR_CONFIRMATION)/CONFIRMED는 여기서만 계산되고 저장되지 않는다 —
 * com.dallim.social.SocialSession의 SocialSessionStatus 문서 참고.
 */
class SocialSessionService(
    private val sessionRepository: SocialSessionRepository,
    private val routeRepository: RouteRepository,
    private val userRepository: UserRepository,
    // 채팅 인박스(GET /users/me/social-sessions, 18장)의 lastMessage 미리보기용. 2단계 채팅
    // 도메인과의 유일한 교차 의존이라 여기 한 곳에만 추가한다.
    private val chatRepository: SocialSessionChatRepository,
) {

    companion object {
        /** 신청 마감: 일정 3일 전부터 신규 신청 차단(2026-09-16 사용자 지시). */
        private val APPLICATION_CLOSE_WINDOW: Duration = Duration.ofDays(3)

        /** 호스트 응답 제한: PENDING 신청은 5시간이 지나면 자동 EXPIRED(2026-09-16 사용자 지시). */
        private val APPLICANT_RESPONSE_WINDOW: Duration = Duration.ofHours(5)
    }

    /** POST /social-sessions — 17.2. 완주 0회면 호스트가 될 수 없다(SESSION_HOST_REQUIRES_FIRST_RUN,
     * 노쇼 호스트 억제 장치 겸 SPEC 요구사항). */
    fun create(hostUserId: String, request: CreateSocialSessionRequest): CreateSocialSessionResponse {
        if (!routeRepository.exists(request.routeId)) {
            throw NotFoundException(ErrorCodes.ROUTE_NOT_FOUND, "코스를 찾을 수 없습니다.")
        }
        // hostUserId는 유효한 JWT에서 왔으므로 항상 존재한다 (com.dallim.meetup.MeetupService
        // 등 다른 도메인과 동일하게 신뢰).
        val host = userRepository.findById(hostUserId)!!
        if (host.totalRuns < 1) {
            throw BadRequestException(
                ErrorCodes.SESSION_HOST_REQUIRES_FIRST_RUN,
                "한 번이라도 달려본 뒤 열 수 있어요.",
            )
        }

        if (request.title.isBlank()) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "제목을 입력해주세요.")
        }
        val scheduledAt = parseInstant(request.scheduledAt)
        if (scheduledAt.isBefore(Instant.now())) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "일정은 현재 시각 이후여야 합니다.")
        }
        if (request.minParticipants < 1 || request.maxParticipants < request.minParticipants || request.maxParticipants > 30) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "인원 설정이 올바르지 않습니다.")
        }

        val sessionId = sessionRepository.create(
            hostUserId = hostUserId,
            routeId = request.routeId,
            title = request.title.trim(),
            scheduledAt = scheduledAt,
            minParticipants = request.minParticipants,
            maxParticipants = request.maxParticipants,
            runningStyles = request.runningStyles,
            beginnerFriendly = request.beginnerFriendly,
            minRunningTemperature = request.minRunningTemperature,
            genderCondition = request.genderCondition,
            description = request.description,
            meetingPointLat = request.meetingPointLat,
            meetingPointLng = request.meetingPointLng,
            meetingPointDescription = request.meetingPointDescription,
            rainPolicy = request.rainPolicy,
        )
        return CreateSocialSessionResponse(sessionId = sessionId)
    }

    /** GET /social-sessions — 17.1. routeId/beginnerFriendly/hasMinTemperature 모두 선택 필터,
     * 날짜·시간대·페이스·거리 등 세밀한 필터는 이번 1단계 범위 밖(과설계 금지, 작업 브리핑 참고). */
    fun list(
        routeId: String?,
        beginnerFriendly: Boolean?,
        hasMinTemperature: Boolean?,
        page: Int,
        size: Int,
    ): SocialSessionListResponse {
        val (rows, totalCount) = sessionRepository.list(routeId, beginnerFriendly, hasMinTemperature, page, size)
        val approvedCounts = sessionRepository.countApprovedByIds(rows.map { it.id })
        val routeCache = HashMap<String, RouteRepository.RouteRow?>()

        val items = rows.map { row ->
            val approvedCount = approvedCounts[row.id] ?: 0
            val route = routeCache.getOrPut(row.routeId) { routeRepository.findDetail(row.routeId) }
            SocialSessionListItem(
                sessionId = row.id,
                title = row.title,
                scheduledAt = row.scheduledAt.toString(),
                routeId = row.routeId,
                routeThumbnailGeoJson = route?.geoJson,
                approvedCount = approvedCount,
                minParticipants = row.minParticipants,
                maxParticipants = row.maxParticipants,
                runningStyles = row.runningStyles,
                hostUserId = row.hostUserId,
                hostNickname = row.hostNickname,
                hostAvatarId = row.hostAvatarId,
                hostRunningTemperature = row.hostRunningTemperature,
                beginnerFriendly = row.beginnerFriendly,
                status = displayStatus(row.status, approvedCount, row.minParticipants, row.scheduledAt),
            )
        }
        return SocialSessionListResponse(items = items, totalCount = totalCount, page = page, size = size)
    }

    /** GET /social-sessions/{id} — 17.3. 비로그인/미승인 참가자는 meetingPointDetail 대신
     * meetingPointHint만 받는다. */
    fun getDetail(sessionId: String, callerUserId: String?): SocialSessionDetailResponse {
        val row = findSessionOr404(sessionId)
        val approvedCount = sessionRepository.countApproved(sessionId)
        val route = routeRepository.findDetail(row.routeId)

        val myApplication = callerUserId?.let { sessionRepository.findApplicant(sessionId, it) }
        val isHost = callerUserId != null && callerUserId == row.hostUserId
        val isApprovedParticipant = myApplication?.status == SocialSessionApplicantStatus.APPROVED

        val meetingPointDetail = if (isHost || isApprovedParticipant) row.meetingPointDescription else null
        val meetingPointHint = buildMeetingPointHint(row.meetingPointDescription)
        val applicationOpen = !Instant.now().isAfter(row.scheduledAt.minus(APPLICATION_CLOSE_WINDOW))

        val participants = sessionRepository.findApprovedApplicants(sessionId).map {
            SocialSessionParticipantItem(userId = it.userId, nickname = it.nickname, avatarId = it.avatarId)
        }

        return SocialSessionDetailResponse(
            sessionId = row.id,
            hostUserId = row.hostUserId,
            hostNickname = row.hostNickname,
            hostAvatarId = row.hostAvatarId,
            hostRunningTemperature = row.hostRunningTemperature,
            routeId = row.routeId,
            routeName = route?.name ?: "",
            routeThumbnailGeoJson = route?.geoJson,
            routeDistanceKm = route?.distanceKm,
            routeEstimatedMinutes = route?.estimatedMinutes,
            title = row.title,
            scheduledAt = row.scheduledAt.toString(),
            minParticipants = row.minParticipants,
            maxParticipants = row.maxParticipants,
            approvedCount = approvedCount,
            runningStyles = row.runningStyles,
            beginnerFriendly = row.beginnerFriendly,
            minRunningTemperature = row.minRunningTemperature,
            genderCondition = row.genderCondition,
            description = row.description,
            meetingPointLat = row.meetingPointLat,
            meetingPointLng = row.meetingPointLng,
            meetingPointDetail = meetingPointDetail,
            meetingPointHint = meetingPointHint,
            rainPolicy = row.rainPolicy,
            status = displayStatus(row.status, approvedCount, row.minParticipants, row.scheduledAt),
            isHost = isHost,
            myApplicationStatus = myApplication?.status,
            participants = participants,
            applicationOpen = applicationOpen,
        )
    }

    /** POST /social-sessions/{id}/apply — 17.4. 조건 미충족 사유는 절대 세분화해서 노출하지
     * 않는다 — 성별이든 온도든 전부 SESSION_CONDITION_NOT_MET + 동일 문구 하나로 통일한다
     * (역추론 방지, S-32 SPEC 핵심 요구사항 — 작업 브리핑 참고).
     *
     * 신청 마감(2026-09-16 사용자 지시): 일정 3일 전이 지나면 신규 신청을 막는다
     * (SESSION_APPLY_WINDOW_CLOSED). 재신청 허용(같은 지시): 기존 신청이 있어도 그게
     * `EXPIRED`(호스트 응답 5시간 초과로 자동 만료)라면 막지 않는다 — 그 외 상태(PENDING/
     * APPROVED/CANCELLED)는 기존 그대로 SESSION_ALREADY_APPLIED. */
    fun apply(sessionId: String, userId: String, request: ApplySocialSessionRequest) {
        val row = findSessionOr404(sessionId)

        if (row.status == SocialSessionStatus.CANCELLED) {
            throw ConflictException(ErrorCodes.SESSION_CANCELLED, "취소된 세션이에요.")
        }
        if (Instant.now().isAfter(row.scheduledAt.minus(APPLICATION_CLOSE_WINDOW))) {
            throw BadRequestException(ErrorCodes.SESSION_APPLY_WINDOW_CLOSED, "마감 3일 전까지만 신청할 수 있어요.")
        }
        if (row.hostUserId == userId) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "본인이 만든 세션에는 신청할 수 없어요.")
        }
        sessionRepository.expirePendingApplicants(sessionId, Instant.now().minus(APPLICANT_RESPONSE_WINDOW))
        val existing = sessionRepository.findApplicant(sessionId, userId)
        if (existing != null && existing.status != SocialSessionApplicantStatus.EXPIRED) {
            throw ConflictException(ErrorCodes.SESSION_ALREADY_APPLIED, "이미 신청한 세션이에요.")
        }
        val approvedCount = sessionRepository.countApproved(sessionId)
        if (approvedCount >= row.maxParticipants) {
            throw BadRequestException(ErrorCodes.SESSION_FULL, "정원이 가득 찼어요.")
        }

        // 둘 다 FK/JWT로 보장된 존재하는 유저 (호스트는 row.hostUserId, 신청자는 currentUserId).
        val host = userRepository.findById(row.hostUserId)!!
        val applicant = userRepository.findById(userId)!!
        if (!meetsConditions(row, host, applicant)) {
            throw BadRequestException(ErrorCodes.SESSION_CONDITION_NOT_MET, "참가 조건이 맞지 않아요.")
        }

        sessionRepository.addApplicant(sessionId, userId, request.message?.trim()?.takeIf { it.isNotBlank() })
    }

    /** POST /social-sessions/{id}/apply/cancel — PENDING 상태에서만 취소 가능. */
    fun cancelApply(sessionId: String, userId: String) {
        findSessionOr404(sessionId)
        val applicant = sessionRepository.findApplicant(sessionId, userId)
            ?: throw NotFoundException(ErrorCodes.SESSION_APPLICATION_NOT_FOUND, "신청 내역을 찾을 수 없습니다.")
        if (applicant.status != SocialSessionApplicantStatus.PENDING) {
            throw BadRequestException(ErrorCodes.SESSION_APPLICATION_NOT_PENDING, "취소할 수 없는 상태예요.")
        }
        sessionRepository.updateApplicantStatus(sessionId, userId, SocialSessionApplicantStatus.CANCELLED)
    }

    /** GET /social-sessions/{id}/applicants — 17.6, 호스트 본인만. 조회 전에 5시간 넘은 PENDING을
     * EXPIRED로 지연 전환한다(2026-09-16 사용자 지시, 스케줄러 없이 터치 시점 전환). */
    fun listApplicants(sessionId: String, callerUserId: String): SocialSessionApplicantsResponse {
        val row = findSessionOr404(sessionId)
        requireHost(row, callerUserId)
        sessionRepository.expirePendingApplicants(sessionId, Instant.now().minus(APPLICANT_RESPONSE_WINDOW))

        val items = sessionRepository.findApplicants(sessionId).map {
            SocialSessionApplicantItem(
                userId = it.userId,
                nickname = it.nickname,
                avatarId = it.avatarId,
                runningTemperature = it.runningTemperature,
                comfortablePace = it.comfortablePace,
                runningExperience = it.runningExperience,
                message = it.message,
                appliedAt = it.appliedAt.toString(),
                respondByAt = it.appliedAt.plus(APPLICANT_RESPONSE_WINDOW).toString(),
                status = it.status,
            )
        }
        return SocialSessionApplicantsResponse(items = items)
    }

    /** POST /social-sessions/{id}/applicants/{userId}/approve — 17.7, 호스트만. 정원 초과 승인
     * 방지(409). 승인 전에 5시간 넘은 PENDING을 EXPIRED로 지연 전환하고(2026-09-16 사용자 지시)
     * 그 결과 방금 EXPIRED가 된 신청이면 SESSION_APPLICATION_NOT_PENDING보다 더 명확한
     * SESSION_APPLICATION_EXPIRED로 알려준다. */
    fun approve(sessionId: String, callerUserId: String, targetUserId: String) {
        val row = findSessionOr404(sessionId)
        requireHost(row, callerUserId)
        sessionRepository.expirePendingApplicants(sessionId, Instant.now().minus(APPLICANT_RESPONSE_WINDOW))

        val applicant = sessionRepository.findApplicant(sessionId, targetUserId)
            ?: throw NotFoundException(ErrorCodes.SESSION_APPLICANT_NOT_FOUND, "신청자를 찾을 수 없습니다.")
        if (applicant.status == SocialSessionApplicantStatus.EXPIRED) {
            throw BadRequestException(ErrorCodes.SESSION_APPLICATION_EXPIRED, "신청이 만료돼서 승인할 수 없어요.")
        }
        if (applicant.status != SocialSessionApplicantStatus.PENDING) {
            throw BadRequestException(ErrorCodes.SESSION_APPLICATION_NOT_PENDING, "이미 처리된 신청이에요.")
        }
        val approvedCount = sessionRepository.countApproved(sessionId)
        if (approvedCount >= row.maxParticipants) {
            throw ConflictException(ErrorCodes.SESSION_FULL, "정원이 가득 찼어요.")
        }
        sessionRepository.updateApplicantStatus(sessionId, targetUserId, SocialSessionApplicantStatus.APPROVED)
    }

    /** DELETE /social-sessions/{id} — 17.8, 호스트만. 참가자가 있어도 그냥 취소(알림 연동은
     * 이번 1단계 범위 밖). */
    fun cancel(sessionId: String, callerUserId: String) {
        val row = findSessionOr404(sessionId)
        requireHost(row, callerUserId)
        sessionRepository.cancel(sessionId)
    }

    /** GET /users/me/social-sessions — 18장, 채팅 인박스. 호스트이거나 APPROVED 참가자인 세션만,
     * 마지막 채팅 메시지 시각 내림차순(메시지가 없는 세션은 뒤로 밀려나 scheduledAt 오름차순으로
     * 정렬 -- "다음 예정 순"이 합리적인 기본값이라 판단, 작업 브리핑의 tie-break 재량 반영). */
    fun listMine(userId: String): MySocialSessionListResponse {
        val rows = sessionRepository.findMine(userId)
        if (rows.isEmpty()) return MySocialSessionListResponse(items = emptyList())

        val approvedCounts = sessionRepository.countApprovedByIds(rows.map { it.id })
        val lastMessages = chatRepository.findLatestBySessionIds(rows.map { it.id })
        val routeCache = HashMap<String, RouteRepository.RouteRow?>()

        data class Enriched(val item: MySocialSessionListItem, val lastMessageAt: Instant?, val scheduledAt: Instant)

        val enriched = rows.map { row ->
            val approvedCount = approvedCounts[row.id] ?: 0
            val route = routeCache.getOrPut(row.routeId) { routeRepository.findDetail(row.routeId) }
            val lastMessageRow = lastMessages[row.id]
            val item = MySocialSessionListItem(
                sessionId = row.id,
                title = row.title,
                routeThumbnailGeoJson = route?.geoJson,
                scheduledAt = row.scheduledAt.toString(),
                isHost = row.hostUserId == userId,
                approvedCount = approvedCount,
                maxParticipants = row.maxParticipants,
                status = displayStatus(row.status, approvedCount, row.minParticipants, row.scheduledAt),
                lastMessage = lastMessageRow?.let {
                    MySocialSessionLastMessage(
                        body = it.body,
                        type = it.type,
                        createdAt = it.createdAt.toString(),
                        senderNickname = it.senderNickname,
                    )
                },
            )
            Enriched(item = item, lastMessageAt = lastMessageRow?.createdAt, scheduledAt = row.scheduledAt)
        }

        val sortedItems = enriched
            .sortedWith(
                compareByDescending<Enriched> { it.lastMessageAt != null }
                    .thenByDescending { it.lastMessageAt ?: Instant.EPOCH }
                    .thenBy { it.scheduledAt },
            )
            .map { it.item }

        return MySocialSessionListResponse(items = sortedItems)
    }

    private fun requireHost(row: SocialSessionRepository.SessionRow, callerUserId: String) {
        if (row.hostUserId != callerUserId) {
            throw ForbiddenException(ErrorCodes.SESSION_NOT_HOST, "호스트만 할 수 있어요.")
        }
    }

    private fun findSessionOr404(sessionId: String): SocialSessionRepository.SessionRow =
        sessionRepository.findById(sessionId) ?: throw NotFoundException(ErrorCodes.SESSION_NOT_FOUND, "세션을 찾을 수 없습니다.")

    /** RECRUITING/NEAR_CONFIRMATION/CONFIRMED/CLOSED는 저장되지 않고 매 조회 시 계산된다
     * (com.dallim.social.SocialSession 문서 참고). "성사까지 2명"(S-32 예시)을 일반화해 남은
     * 인원이 2명 이하면 NEAR_CONFIRMATION으로 본다.
     *
     * 우선순위(2026-09-16 사용자 지시로 CLOSED 추가): CANCELLED(최우선 — 취소된 세션은 시간이
     * 지나도 여전히 CANCELLED) > CLOSED(현재 시각이 scheduledAt을 지났으면) > CONFIRMED >
     * NEAR_CONFIRMATION > RECRUITING. */
    private fun displayStatus(
        stored: SocialSessionStatus,
        approvedCount: Int,
        minParticipants: Int,
        scheduledAt: Instant,
    ): SocialSessionDisplayStatus {
        if (stored == SocialSessionStatus.CANCELLED) return SocialSessionDisplayStatus.CANCELLED
        if (Instant.now().isAfter(scheduledAt)) return SocialSessionDisplayStatus.CLOSED
        if (approvedCount >= minParticipants) return SocialSessionDisplayStatus.CONFIRMED
        val remaining = minParticipants - approvedCount
        return if (remaining <= 2) SocialSessionDisplayStatus.NEAR_CONFIRMATION else SocialSessionDisplayStatus.RECRUITING
    }

    /**
     * 참가 조건 검증 — 성별/온도만 서버가 실제로 막는다. 러닝 스타일(페이스 대체)/초보환영은
     * SPEC이 구체적인 매칭 규칙을 정의하지 않은 채 "4종"/boolean으로만 남겨서, 이번 1단계에서는
     * 표시용 정보로만 쓰고 신청을 막는 조건으로 삼지 않는다(과설계 금지 — 임의로 매칭 알고리즘을
     * 만들면 SPEC에 없는 규칙을 발명하는 셈이다). 작업 브리핑도 이 두 가지만 구체적인 저장 컬럼과
     * 동작을 정의했다.
     */
    private fun meetsConditions(row: SocialSessionRepository.SessionRow, host: User, applicant: User): Boolean {
        if (!matchesGenderCondition(row.genderCondition, hostGender = host.gender, applicantGender = applicant.gender)) {
            return false
        }
        val minTemp = row.minRunningTemperature
        if (minTemp != null && applicant.runningTemperature < minTemp) {
            return false
        }
        return true
    }

    /** gender는 free-form varchar이지만 android/app/.../ProfileOptions.kt가 실제로 쓰는 값은
     * "MALE"/"FEMALE"/"PREFER_NOT_TO_SAY" 셋뿐이다. SAME_AS_HOST는 둘 다 MALE/FEMALE로 명확히
     * 밝혔을 때만 통과시킨다 -- 성별을 밝히지 않은 유저를 억지로 매칭시키지 않는 게 안전한
     * 기본값이다. */
    private fun matchesGenderCondition(
        condition: SocialSessionGenderCondition,
        hostGender: String?,
        applicantGender: String?,
    ): Boolean = when (condition) {
        SocialSessionGenderCondition.ANY -> true
        SocialSessionGenderCondition.FEMALE_ONLY -> applicantGender == "FEMALE"
        SocialSessionGenderCondition.MALE_ONLY -> applicantGender == "MALE"
        SocialSessionGenderCondition.SAME_AS_HOST ->
            (hostGender == "MALE" || hostGender == "FEMALE") && applicantGender == hostGender
    }

    /** 승인 전/비로그인 상대에게 보여줄 대략적인 집결지 힌트 — 상세 설명의 첫 단어만 잘라서
     * "○○ 인근"으로 뭉뚱그린다(예: "안양천 삼성교 밑 벤치 앞" -> "안양천 인근"). SketchRoute에는
     * region 컬럼이 없어 그쪽 재사용은 불가능해 이 방식으로 단순화했다. */
    private fun buildMeetingPointHint(meetingPointDescription: String?): String {
        val firstToken = meetingPointDescription?.trim()?.split(Regex("\\s+"))?.firstOrNull { it.isNotBlank() }
        return if (firstToken.isNullOrBlank()) {
            "집결지는 참가 승인 후 확인할 수 있어요"
        } else {
            "$firstToken 인근"
        }
    }

    private fun parseInstant(raw: String): Instant = try {
        Instant.parse(raw)
    } catch (e: Exception) {
        throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "시간 형식이 올바르지 않습니다.")
    }
}
