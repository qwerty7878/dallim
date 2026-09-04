package com.dallim.push

import kotlinx.serialization.Serializable

/** POST /users/me/device-tokens request body (docs/02-api-spec.md 10.1). */
@Serializable
data class DeviceTokenRequest(
    val fcmToken: String,
    val platform: DevicePlatform,
)
