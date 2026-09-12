package com.dallim.social

import com.dallim.common.BadRequestException
import com.dallim.common.ErrorCodes
import com.dallim.common.ForbiddenException
import com.dallim.common.NotFoundException
import com.dallim.user.UserRepository
import java.time.Instant

/**
 * S-38 세션 종료 후 평가 / S-39 Running Mate 매칭 판단 — docs/02-api-spec.md 17장 이어서. 별점
 * 없음, 부정 평가 태그 없음(불만은 com.dallim.social.SocialSessionChatService의 신고 경로로만).
 * 평가는 같이 체크인(CHECKED_IN/LATE)한 참가자 사이에서만 오갈 수 있다.
 */
class SocialSessionFeedbackService(
    private val sessionRepository: SocialSessionRepository,
    private val checkinRepository: SocialSessionCheckinRepository,
    private val feedbackRepository: SocialSessionFeedbackRepository,
    private val runningMateRepository: RunningMateRepository,
    private val userRepository: UserRepository,
) {
    companion object {
        // 태그 1개당 +0.1, 최대 3개라 한 세션 평가당 최대 +0.3까지 -- 당근마켓 매너온도류 관례를
        // 따라 작게 시작한 기본값(RunJudgementService의 임계값들과 같은 정신으로 "합리적 기본값,
        // 조정 가능"). 재제출(덮어쓰기) 시엔 이전 가산분을 제하고 새 가산분만 반영해서(아래
        // applyTemperatureDelta) 태그를 반복 갈아치우며 매너온도를 무한히 올리는 걸 막는다.
        private const val TAG_BONUS_PER_TAG = 0.1
    }

    /** GET /social-sessions/{id}/feedback-targets — 나 자신 제외, 같이 체크인(CHECKED_IN/LATE)한
     * 호스트+참가자만. 호출자 본인도 체크인 상태여야 한다. */
    fun listTargets(sessionId: String, callerUserId: String): FeedbackTargetsResponse {
        val session = findSessionOr404(sessionId)
        requireCheckedIn(session.id, callerUserId)

        val participantIds = listOf(session.hostUserId) + sessionRepository.findApprovedApplicants(sessionId).map { it.userId }
        val eligibleIds = participantIds.filter { it != callerUserId && isCheckedIn(sessionId, it) }
        val usersById = userRepository.findByIds(eligibleIds).associateBy { it.id }

        val items = eligibleIds.mapNotNull { userId ->
            usersById[userId]?.let {
                FeedbackTargetItem(userId = it.id, nickname = it.nickname ?: "달림이", avatarId = it.avatarId, isHost = userId == session.hostUserId)
            }
        }
        return FeedbackTargetsResponse(items = items)
    }

    /** POST /social-sessions/{id}/feedback. */
    fun submit(sessionId: String, callerUserId: String, request: SubmitFeedbackRequest): SubmitFeedbackResult {
        val session = findSessionOr404(sessionId)
        requireCheckedIn(session.id, callerUserId)

        if (request.targetUserId == callerUserId) {
            throw BadRequestException(ErrorCodes.SESSION_FEEDBACK_TARGET_NOT_ELIGIBLE, "본인은 평가할 수 없어요.")
        }
        val targetIsParticipant = request.targetUserId == session.hostUserId ||
            sessionRepository.findApplicant(sessionId, request.targetUserId)?.status == SocialSessionApplicantStatus.APPROVED
        if (!targetIsParticipant || !isCheckedIn(sessionId, request.targetUserId)) {
            throw BadRequestException(ErrorCodes.SESSION_FEEDBACK_TARGET_NOT_ELIGIBLE, "평가할 수 없는 대상이에요.")
        }

        if (request.tags.size > SocialFeedbackTags.MAX_TAGS_PER_SUBMISSION) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "태그는 최대 ${SocialFeedbackTags.MAX_TAGS_PER_SUBMISSION}개까지 선택할 수 있습니다.")
        }
        val invalidTags = request.tags.filterNot { it in SocialFeedbackTags.ALLOWED }
        if (invalidTags.isNotEmpty()) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "허용되지 않은 태그가 있습니다: $invalidTags")
        }

        val previous = feedbackRepository.find(sessionId, callerUserId, request.targetUserId)
        feedbackRepository.upsert(sessionId, callerUserId, request.targetUserId, request.tags, request.wantsToRunAgain)
        applyTemperatureDelta(request.targetUserId, previousTagCount = previous?.tags?.size ?: 0, newTagCount = request.tags.size)

        val mateEstablished = if (request.wantsToRunAgain) {
            val reciprocal = feedbackRepository.find(sessionId, request.targetUserId, callerUserId)
            if (reciprocal?.wantsToRunAgain == true) {
                runningMateRepository.upsertOnMatch(callerUserId, request.targetUserId, Instant.now())
                true
            } else {
                false
            }
        } else {
            false
        }
        return SubmitFeedbackResult(mateEstablished = mateEstablished)
    }

    private fun applyTemperatureDelta(targetUserId: String, previousTagCount: Int, newTagCount: Int) {
        val delta = (newTagCount - previousTagCount) * TAG_BONUS_PER_TAG
        if (delta != 0.0) {
            userRepository.adjustRunningTemperature(targetUserId, delta)
        }
    }

    private fun isCheckedIn(sessionId: String, userId: String): Boolean {
        val status = checkinRepository.find(sessionId, userId)?.status ?: return false
        return status == SocialSessionCheckinStatus.CHECKED_IN || status == SocialSessionCheckinStatus.LATE
    }

    private fun requireCheckedIn(sessionId: String, userId: String) {
        if (!isCheckedIn(sessionId, userId)) {
            throw ForbiddenException(ErrorCodes.SESSION_FEEDBACK_NOT_ELIGIBLE, "체크인한 참가자만 평가할 수 있어요.")
        }
    }

    private fun findSessionOr404(sessionId: String): SocialSessionRepository.SessionRow =
        sessionRepository.findById(sessionId) ?: throw NotFoundException(ErrorCodes.SESSION_NOT_FOUND, "세션을 찾을 수 없습니다.")
}
