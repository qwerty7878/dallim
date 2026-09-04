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
    val osrm: OsrmSettings,
    val fcm: FcmSettings,
    val kakao: KakaoSettings,
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

    data class OsrmSettings(
        val baseUrl: String,
        // docs/02-api-spec.md 13.2 — SHAPE-mode discovery routes through a separate OSRM
        // instance/profile (dallim-foot-shape.lua, service-belt-only dataset) than [baseUrl]'s
        // LOOP/POINT_TO_POINT/requiredWaypoints instance.
        val shapeBaseUrl: String,
    )

    /**
     * docs/02-api-spec.md 10.2 — path to the Firebase Admin SDK service account JSON. Never the
     * key content itself, just where to find it on disk; the file is gitignored
     * (backend/secrets/, see backend/.gitignore) and must never be committed or hardcoded
     * elsewhere in the codebase.
     */
    data class FcmSettings(
        val credentialsPath: String,
    )

    /**
     * docs/02-api-spec.md 12.2 — REST API key for Kakao Local's keyword search
     * (`GET /v2/local/search/keyword.json`), used by place search (GET /routes/places/search).
     * Deliberately separate from [OAuthSettings.kakaoUserInfoUrl]'s login key (12장 prose: "카카오
     * 로그인용 키와는 별도 발급"). Blank/unset (local dev without the key configured) is a valid,
     * expected value — com.dallim.common.KakaoLocalClient treats it as "skip the Kakao call,
     * return no results" rather than failing, same as FcmSettings.credentialsPath pointing at a
     * missing file.
     */
    data class KakaoSettings(
        val localApiKey: String,
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
    val osrm = config("osrm")
    val fcm = config("fcm")
    val kakao = config("kakao")

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
        osrm = DallimConfig.OsrmSettings(
            baseUrl = osrm.property("baseUrl").getString(),
            shapeBaseUrl = osrm.property("shapeBaseUrl").getString(),
        ),
        fcm = DallimConfig.FcmSettings(
            credentialsPath = fcm.property("credentialsPath").getString(),
        ),
        kakao = DallimConfig.KakaoSettings(
            localApiKey = kakao.propertyOrNull("localApiKey")?.getString().orEmpty(),
        ),
    )
}
