package com.dallim.social

import com.dallim.common.BadRequestException
import com.dallim.common.ErrorCodes
import com.dallim.common.ForbiddenException
import com.dallim.common.NotFoundException
import com.dallim.user.UserRepository
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * S-35 팀 채팅 비즈니스 로직 — docs/02-api-spec.md 17장 이어서. SocialSessionChatRoutes.kt는
 * 얇은 HTTP/WebSocket 어댑터로 남기고, 접근 제어/생명주기/취소 연동은 전부 여기 둔다
 * (com.dallim.social.SocialSessionService와 동일 원칙).
 */
class SocialSessionChatService(
    private val sessionRepository: SocialSessionRepository,
    private val chatRepository: SocialSessionChatRepository,
    private val userRepository: UserRepository,
    private val chatHub: SocialSessionChatHub,
) {
    companion object {
        // 세션 "종료"를 이 앱엔 없는 별도 액션 대신 달림 시작(started_at, S-37)으로 근사한다 --
        // com.dallim.social.SocialSessionTable.startedAt 문서 참고. 합리적 기본값, 조정 가능.
        private val READ_ONLY_AFTER: Duration = Duration.ofHours(24)
        private val ACCESS_ENDS_AFTER: Duration = Duration.ofDays(7)

        // 최근 30일 내 "당일 취소"(오늘 참가 어려워요) 2회부터 매너온도 감점 -- 당근마켓 매너온도류
        // 관례를 따라 작게 시작한 기본값. RunJudgementService의 임계값들과 같은 정신으로, "합리적
        // 기본값, 조정 가능"으로 여기 문서화해 둔다. qa-engineer의 검증 대상은 아니다(러닝 완주
        // 판정과는 완전히 별개 기능).
        private val SAME_DAY_CANCEL_LOOKBACK: Duration = Duration.ofDays(30)
        private const val SAME_DAY_CANCEL_THRESHOLD = 2
        private const val SAME_DAY_CANCEL_BASE_PENALTY = -0.5
        private const val SAME_DAY_CANCEL_REPEAT_EXTRA_PENALTY = -0.3
    }

    private fun canAccess(session: SocialSessionRepository.SessionRow, userId: String): Boolean =
        session.hostUserId == userId ||
            sessionRepository.findApplicant(session.id, userId)?.status == SocialSessionApplicantStatus.APPROVED

    /** WebSocket 연결 시점 / REST 조회 공통 접근 제어 -- 호스트도 아니고 승인된 참가자도 아니면
     * 403 SESSION_NOT_PARTICIPANT. */
    fun requireAccess(sessionId: String, userId: String): SocialSessionRepository.SessionRow {
        val session = sessionRepository.findById(sessionId)
            ?: throw NotFoundException(ErrorCodes.SESSION_NOT_FOUND, "세션을 찾을 수 없습니다.")
        if (!canAccess(session, userId)) {
            throw ForbiddenException(ErrorCodes.SESSION_NOT_PARTICIPANT, "채팅에 접근할 수 없어요.")
        }
        return session
    }

    /** 시작 +7일 후 조회 자체를 막는다. */
    fun assertReadable(session: SocialSessionRepository.SessionRow) {
        val startedAt = session.startedAt ?: return
        if (Instant.now().isAfter(startedAt.plus(ACCESS_ENDS_AFTER))) {
            throw ForbiddenException(ErrorCodes.SESSION_CHAT_ACCESS_EXPIRED, "채팅 접근 기간이 끝났어요.")
        }
    }

    /** 시작 +24시간 후엔 조회는 되지만 전송은 막는다(읽기 전용). */
    private fun isWritable(session: SocialSessionRepository.SessionRow): Boolean {
        val startedAt = session.startedAt ?: return true
        return !Instant.now().isAfter(startedAt.plus(READ_ONLY_AFTER))
    }

    /** GET .../chat/messages (🔒). */
    fun listMessages(sessionId: String, userId: String, page: Int, size: Int): ChatMessageListResponse {
        val session = requireAccess(sessionId, userId)
        assertReadable(session)
        val (rows, totalCount) = chatRepository.findPage(sessionId, page, size)
        return ChatMessageListResponse(items = rows.map { it.toItem() }, totalCount = totalCount, page = page, size = size)
    }

    /**
     * WebSocket으로 들어온 메시지 처리 -- 검증 + 저장 + 브로드캐스트 + (해당하면) Quick Message
     * "오늘 참가 어려워요"의 취소 연동까지 전부 여기서 끝낸다. 읽기 전용 구간(+24h 경과)이면
     * 아무것도 저장/브로드캐스트하지 않고 null을 반환한다 -- 호출부(Routes)가 이를 보고 발신자
     * 한 명에게만 안내 프레임을 보낸다.
     */
    suspend fun handleIncoming(
        sessionId: String,
        session: SocialSessionRepository.SessionRow,
        senderUserId: String,
        frame: IncomingChatFrame,
    ): ChatMessageItem? {
        if (!isWritable(session)) return null

        val body = frame.body.trim()
        if (frame.type == IncomingChatMessageType.QUICK_MESSAGE && body !in QuickMessages.ALLOWED) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "정해진 문구 중 하나만 보낼 수 있어요.")
        }
        if (frame.type == IncomingChatMessageType.TEXT && body.isBlank()) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "메시지를 입력해주세요.")
        }

        // 호스트의 TEXT는 서버가 자동으로 HOST_ANNOUNCEMENT로 승격한다(작업 브리핑의 두 옵션 중
        // "sender가 host_user_id와 같으면 서버가 자동으로 분류" 쪽을 택함 -- 클라이언트가 타입을
        // 잘못 보낼 여지를 아예 없앤다).
        val isHost = session.hostUserId == senderUserId
        val storedType = if (frame.type == IncomingChatMessageType.TEXT && isHost) {
            ChatMessageType.HOST_ANNOUNCEMENT
        } else {
            ChatMessageType.valueOf(frame.type.name)
        }

        val row = chatRepository.insert(sessionId, senderUserId, storedType, body)
        val item = row.toItem()
        chatHub.broadcast(sessionId, item)

        if (frame.type == IncomingChatMessageType.QUICK_MESSAGE && body == QuickMessages.CANT_MAKE_IT) {
            handleCantMakeItWithdrawal(sessionId, session, senderUserId, frame.cancelReason?.trim()?.takeIf { it.isNotBlank() })
        }
        return item
    }

    /**
     * "오늘 참가 어려워요"를 보내면 참가 취소로 이어진다(1단계 apply/cancel과는 별개 경로 --
     * 그건 PENDING 상태에서만 쓸 수 있는 신청 철회고, 이건 승인된 참가라도 세션 당일 취소를
     * 다루기 때문에 상태 제약 없이 CANCELLED로 전환한다). 이미 취소된 신청이면 조용히 no-op.
     */
    private suspend fun handleCantMakeItWithdrawal(
        sessionId: String,
        session: SocialSessionRepository.SessionRow,
        userId: String,
        cancelReason: String?,
    ) {
        val applicant = sessionRepository.findApplicant(sessionId, userId) ?: return
        if (applicant.status == SocialSessionApplicantStatus.CANCELLED) return

        sessionRepository.updateApplicantStatus(sessionId, userId, SocialSessionApplicantStatus.CANCELLED)

        if (cancelReason != null) {
            val note = chatRepository.insert(sessionId, null, ChatMessageType.SYSTEM, "취소 사유: $cancelReason")
            chatHub.broadcast(sessionId, note.toItem())
        }

        // "당일" 판정은 응답 시각과 세션 일정이 같은 날(UTC 기준)인지로 근사한다 -- 타임존별 오차는
        // 감내 가능한 수준(작업 브리핑 참고, 과설계 금지).
        val now = Instant.now()
        val isSameDayCancel = session.scheduledAt.truncatedTo(ChronoUnit.DAYS) == now.truncatedTo(ChronoUnit.DAYS)
        if (!isSameDayCancel) return

        val recentSameDayCancels = sessionRepository.countSameDayCancellationsSince(userId, now.minus(SAME_DAY_CANCEL_LOOKBACK))
        if (recentSameDayCancels >= SAME_DAY_CANCEL_THRESHOLD) {
            val extra = if (recentSameDayCancels > SAME_DAY_CANCEL_THRESHOLD) SAME_DAY_CANCEL_REPEAT_EXTRA_PENALTY else 0.0
            userRepository.adjustRunningTemperature(userId, SAME_DAY_CANCEL_BASE_PENALTY + extra)
        }
    }

    /** POST .../chat/messages/{messageId}/report (🔒). */
    fun reportMessage(sessionId: String, messageId: String, reporterUserId: String, reason: String?) {
        requireAccess(sessionId, reporterUserId)
        chatRepository.findById(sessionId, messageId)
            ?: throw NotFoundException(ErrorCodes.CHAT_MESSAGE_NOT_FOUND, "메시지를 찾을 수 없습니다.")
        chatRepository.report(reporterUserId, ContentReportTargetType.CHAT_MESSAGE, messageId, reason?.trim()?.takeIf { it.isNotBlank() })
    }

    /** POST /social-sessions/{id}/report (🔒) -- 1단계 S-32 "[신고]" gap을 이번에 같이 닫는다. */
    fun reportSession(sessionId: String, reporterUserId: String, reason: String?) {
        sessionRepository.findById(sessionId) ?: throw NotFoundException(ErrorCodes.SESSION_NOT_FOUND, "세션을 찾을 수 없습니다.")
        chatRepository.report(reporterUserId, ContentReportTargetType.SOCIAL_SESSION, sessionId, reason?.trim()?.takeIf { it.isNotBlank() })
    }

    private fun SocialSessionChatRepository.ChatMessageRow.toItem() = ChatMessageItem(
        id = id,
        senderUserId = senderUserId,
        senderNickname = senderNickname,
        senderAvatarId = senderAvatarId,
        type = type,
        body = body,
        createdAt = createdAt.toString(),
    )
}
