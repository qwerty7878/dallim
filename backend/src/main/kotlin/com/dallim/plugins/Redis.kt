package com.dallim.plugins

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

/**
 * Lettuce Redis wiring. Currently consumed only by auth/RefreshTokenStore.kt (opaque refresh
 * tokens, TTL = refreshTokenExpiryDays, rotation on use — see plugins/Security.kt for why
 * refresh tokens are NOT JWTs). The route domain's `finisherCount` Redis INCR
 * (docs/01-feature-spec.md 2.2.D) will reuse the same connection bean once that domain lands.
 */
object RedisFactory {
    fun client(settings: DallimConfig.RedisSettings): RedisClient = RedisClient.create(settings.uri)

    fun connection(client: RedisClient): StatefulRedisConnection<String, String> = client.connect()
}
