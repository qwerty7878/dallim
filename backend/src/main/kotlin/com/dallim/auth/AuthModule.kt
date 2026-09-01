package com.dallim.auth

import com.dallim.plugins.DallimConfig
import org.koin.dsl.module

/**
 * Koin module for the auth domain (Google/Kakao/Email login, JWT issuance, BCrypt).
 * See docs/01-feature-spec.md 2.2.A and docs/02-api-spec.md 1장.
 *
 * Depends on core beans registered in plugins/Koin.kt: DallimConfig, Database,
 * StatefulRedisConnection<String, String> (plugins/Redis.kt), HttpClient (common/HttpClientFactory.kt).
 */
val authModule = module {
    single { UserAccountRepository(get()) }
    single { JwtService(get<DallimConfig>().jwt) }
    single { RefreshTokenStore(get(), get<DallimConfig>().jwt.refreshTokenExpiryDays) }
    single { GoogleAuthClient(get(), get<DallimConfig>().oauth.googleClientId) }
    single { KakaoAuthClient(get(), get<DallimConfig>().oauth.kakaoUserInfoUrl) }
    single { AuthService(get(), get(), get(), get(), get()) }
}
