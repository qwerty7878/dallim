package com.dallim.social

import com.dallim.user.UserRepository

/** S-39 Running Mate 목록/해제 — docs/02-api-spec.md 17장 이어서. 매칭 성립 자체는
 * com.dallim.social.SocialSessionFeedbackService.submit에서 일어난다(피드백 제출 순간). */
class RunningMateService(
    private val runningMateRepository: RunningMateRepository,
    private val userRepository: UserRepository,
) {
    /** GET /users/me/running-mates. */
    fun listMine(userId: String): RunningMateListResponse {
        val rows = runningMateRepository.findVisibleMates(userId)
        val usersById = userRepository.findByIds(rows.map { it.mateUserId }).associateBy { it.id }
        val items = rows.mapNotNull { row ->
            usersById[row.mateUserId]?.let {
                RunningMateItem(
                    userId = it.id,
                    nickname = it.nickname ?: "달림이",
                    avatarId = it.avatarId,
                    runTogetherCount = row.runTogetherCount,
                    lastRunTogetherAt = row.lastRunTogetherAt.toString(),
                )
            }
        }
        return RunningMateListResponse(items = items)
    }

    /** DELETE /users/me/running-mates/{mateUserId} — 조용히 단방향 해제, 상대에게 알림 없음
     * (SPEC 명시). 이미 없는 관계여도 그냥 성공(멱등). */
    fun hide(userId: String, mateUserId: String) {
        runningMateRepository.hideForUser(userId, mateUserId)
    }
}
