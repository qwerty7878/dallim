package com.dallim.user

import com.dallim.common.BadRequestException
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

    /**
     * PATCH /users/me (2026-09-16 사용자 지시) — 온보딩 1회성 등록(registerProfile)과 별개로
     * 닉네임/아바타만 나중에 바꾸는 용도. 둘 다 optional, 보낸(null이 아닌) 필드만 갱신한다.
     * 닉네임을 본인의 현재 닉네임과 동일하게 보내면(변경 없음) 중복 검사를 건너뛴다 — 그대로
     * nicknameExists를 돌리면 자기 자신과 충돌해서 항상 409가 나기 때문이다. 아바타는 온보딩과
     * 같은 6종 값 집합을 쓰지만 화이트리스트 검증은 하지 않는다(안드로이드가 정해진 값만 보냄,
     * 과설계 금지 — 작업 브리핑 참고).
     */
    fun updateProfileFields(userId: String, request: PatchMeRequest): UserMeResponse {
        val user = requireNotNull(userRepository.findById(userId)) {
            "authenticated user $userId has no user row -- should be unreachable, JWTs are only issued for existing rows"
        }

        val nickname = request.nickname?.trim()
        if (nickname != null) {
            if (nickname.isBlank()) {
                throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "닉네임을 입력해주세요.")
            }
            if (nickname != user.nickname && userRepository.nicknameExists(nickname)) {
                throw ConflictException(ErrorCodes.NICKNAME_TAKEN, "이미 사용 중인 닉네임입니다.")
            }
        }

        try {
            userRepository.updateProfileFields(
                userId = userId,
                nickname = nickname,
                avatarId = request.avatarId,
            )
        } catch (e: NicknameTakenException) {
            throw ConflictException(ErrorCodes.NICKNAME_TAKEN, "이미 사용 중인 닉네임입니다.")
        }

        return getMe(userId)
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
