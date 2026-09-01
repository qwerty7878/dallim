package com.dallim.plugins

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig

/**
 * Typed view over the `dallim { ... }` block in application.conf / application-prod.conf.
 * Centralizes config lookups so no module reads raw HOCON paths directly.
 */
data class DallimConfig(
    val profile: String,
    val jwt: JwtSettings,
    val database: DatabaseSettings,
    val redis: RedisSettings,
    val oauth: OAuthSettings,
) {
    data class JwtSettings(
        val secret: String,
        val issuer: String,
        val audience: String,
        val realm: String,
        val accessTokenExpiryMinutes: Long,
        val refreshTokenExpiryDays: Long,
    )

    data class DatabaseSettings(
        val jdbcUrl: String,
        val user: String,
        val password: String,
        val maxPoolSize: Int,
    )

    data class RedisSettings(
        val uri: String,
    )

    data class OAuthSettings(
        val googleClientId: String,
        val kakaoUserInfoUrl: String,
    )
}

fun Application.loadDallimConfig(): DallimConfig {
    val root = environment.config.config("dallim")
    return root.toDallimConfig()
}

private fun ApplicationConfig.toDallimConfig(): DallimConfig {
    val jwt = config("jwt")
    val db = config("database")
    val redis = config("redis")
    val oauth = config("oauth")

    return DallimConfig(
        profile = property("profile").getString(),
        jwt = DallimConfig.JwtSettings(
            secret = jwt.property("secret").getString(),
            issuer = jwt.property("issuer").getString(),
            audience = jwt.property("audience").getString(),
            realm = jwt.property("realm").getString(),
            accessTokenExpiryMinutes = jwt.property("accessTokenExpiryMinutes").getString().toLong(),
            refreshTokenExpiryDays = jwt.property("refreshTokenExpiryDays").getString().toLong(),
        ),
        database = DallimConfig.DatabaseSettings(
            jdbcUrl = db.property("jdbcUrl").getString(),
            user = db.property("user").getString(),
            password = db.property("password").getString(),
            maxPoolSize = db.property("maxPoolSize").getString().toInt(),
        ),
        redis = DallimConfig.RedisSettings(
            uri = redis.property("uri").getString(),
        ),
        oauth = DallimConfig.OAuthSettings(
            googleClientId = oauth.config("google").property("clientId").getString(),
            kakaoUserInfoUrl = oauth.config("kakao").property("userInfoUrl").getString(),
        ),
    )
}
