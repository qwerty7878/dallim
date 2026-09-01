package com.dallim.user

import com.dallim.common.ConflictException
import com.dallim.common.ErrorCodes

/**
 * Profile business logic — docs/02-api-spec.md 2장 (nickname-check / POST /users/me/profile /
 * GET /users/me). Saved-routes logic stays in SavedRouteService; this is the rest of the user
 * domain for this round.
 *
 * gender is accepted on [ProfileRequest] and stored, but is never read back onto a response DTO
 * here (CLAUDE.md rule 2) -- see [toMeResponse].
 */
class UserService(private val userRepository: UserRepository) {

    fun checkNickname(nickname: String): NicknameCheckResponse =
        NicknameCheckResponse(available = !userRepository.nicknameExists(nickname))

    /**
     * Checks nickname availability up front for a fast, friendly error path, then relies on the
     * unique index as the actual final defense against the TOCTOU race between this check and
     * the write (docs/02-api-spec.md 2장 note, CLAUDE.md task brief rule 2).
     */
    fun registerProfile(userId: String, request: ProfileRequest): ProfileResponse {
        if (userRepository.nicknameExists(request.nickname)) {
            throw ConflictException(ErrorCodes.NICKNAME_TAKEN, "이미 사용 중인 닉네임입니다.")
        }

        try {
            userRepository.registerProfile(
                userId = userId,
                nickname = request.nickname,
                avatarId = request.avatarId,
                runningExperience = request.runningExperience,
                comfortablePace = request.comfortablePace,
                gender = request.gender,
            )
        } catch (e: NicknameTakenException) {
            throw ConflictException(ErrorCodes.NICKNAME_TAKEN, "이미 사용 중인 닉네임입니다.")
        }

        return ProfileResponse(userId = userId, nickname = request.nickname)
    }

    fun getMe(userId: String): UserMeResponse {
        val user = requireNotNull(userRepository.findById(userId)) {
            "authenticated user $userId has no user row -- should be unreachable, JWTs are only issued for existing rows"
        }
        return user.toMeResponse()
    }

    // Deliberately does NOT touch user.gender -- see class doc / CLAUDE.md rule 2.
    private fun User.toMeResponse() = UserMeResponse(
        userId = id,
        nickname = nickname.orEmpty(),
        avatarId = avatarId.orEmpty(),
        runningExperience = runningExperience.orEmpty(),
        comfortablePace = comfortablePace.orEmpty(),
        totalRuns = totalRuns,
        totalDistanceKm = totalDistanceKm,
    )
}
