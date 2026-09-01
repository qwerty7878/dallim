package com.dallim.app.onboarding.login

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction over the Google/Kakao SDK sign-in flows so [LoginViewModel] doesn't depend on
 * either SDK directly. Real key/SDK integration is explicitly out of scope for this round
 * (see task brief) — [NoOpSocialLoginLauncher] below is a stand-in that always reports
 * "not implemented" so the button UI and ViewModel wiring can be finished now, and swapped
 * for a real implementation later without touching LoginScreen/LoginViewModel.
 */
interface SocialLoginLauncher {
    /** Launches Google Sign-In and resolves with a Google ID Token (docs/02-api-spec.md 1장 — `idToken`). */
    suspend fun launchGoogleSignIn(): Result<String>

    /** Launches Kakao login and resolves with a Kakao Access Token (docs/02-api-spec.md 1장 — `kakaoAccessToken`). */
    suspend fun launchKakaoSignIn(): Result<String>
}

class NoOpSocialLoginLauncher @Inject constructor() : SocialLoginLauncher {
    // TODO(android-dev): integrate the real Google Identity Services (Credential Manager /
    // GoogleSignIn) SDK here — obtain a Google ID Token and return it via Result.success(idToken).
    override suspend fun launchGoogleSignIn(): Result<String> =
        Result.failure(NotImplementedError("Google Sign-In SDK 연동 예정 — 이번 라운드 범위 밖"))

    // TODO(android-dev): integrate the real Kakao SDK (UserApiClient.instance.loginWithKakaoTalk /
    // loginWithKakaoAccount) here — obtain a Kakao Access Token and return it via Result.success(token).
    override suspend fun launchKakaoSignIn(): Result<String> =
        Result.failure(NotImplementedError("Kakao SDK 연동 예정 — 이번 라운드 범위 밖"))
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SocialLoginModule {
    @Binds
    @Singleton
    abstract fun bindSocialLoginLauncher(impl: NoOpSocialLoginLauncher): SocialLoginLauncher
}
