package com.dallim.user

import com.dallim.common.BadRequestException
import com.dallim.common.ErrorCodes
import com.dallim.common.NotFoundException

/**
 * 유저 차단(18장) 비즈니스 로직 — 스코프는 "채팅 메시지 발신자 차단"뿐이다. 세션 신청/매칭 등
 * 다른 곳으로 차단 효과를 전파하지 않는다(이번 라운드 범위 밖, 사용자 지시). 서버는 CRUD만
 * 제공하고 채팅 메시지 자체를 필터링하지 않는다 -- 안드로이드가 GET /users/me/blocks 결과로
 * 화면에서 직접 걸러낸다.
 */
class BlockedUserService(
    private val blockedUserRepository: BlockedUserRepository,
    private val userRepository: UserRepository,
) {

    /** POST /users/me/blocks — 자기 자신 차단은 400 VALIDATION_ERROR, 존재하지 않는 유저는
     * 404 BLOCK_TARGET_NOT_FOUND. 이미 차단했으면 멱등하게 성공. */
    fun block(blockerUserId: String, request: BlockUserRequest) {
        if (request.blockedUserId == blockerUserId) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "자기 자신은 차단할 수 없어요.")
        }
        if (userRepository.findById(request.blockedUserId) == null) {
            throw NotFoundException(ErrorCodes.BLOCK_TARGET_NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }
        blockedUserRepository.block(blockerUserId, request.blockedUserId)
    }

    /** DELETE /users/me/blocks/{blockedUserId} — 관계가 없어도 멱등하게 성공. */
    fun unblock(blockerUserId: String, blockedUserId: String) {
        blockedUserRepository.unblock(blockerUserId, blockedUserId)
    }

    /** GET /users/me/blocks. */
    fun listBlocked(blockerUserId: String): BlockedUsersResponse {
        val items = blockedUserRepository.findBlocked(blockerUserId).map {
            BlockedUserItem(
                userId = it.userId,
                nickname = it.nickname,
                avatarId = it.avatarId,
                blockedAt = it.blockedAt.toString(),
            )
        }
        return BlockedUsersResponse(items = items)
    }
}
