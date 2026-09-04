package com.dallim.push

import com.dallim.common.BadRequestException
import com.dallim.common.ErrorCodes

/** POST /users/me/device-tokens business logic (docs/02-api-spec.md 10.1). */
class DeviceTokenService(private val deviceTokenRepository: DeviceTokenRepository) {

    fun registerToken(userId: String, request: DeviceTokenRequest) {
        if (request.fcmToken.isBlank()) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "fcmToken은 비어 있을 수 없습니다.")
        }
        deviceTokenRepository.upsert(userId, request.fcmToken, request.platform)
    }
}
