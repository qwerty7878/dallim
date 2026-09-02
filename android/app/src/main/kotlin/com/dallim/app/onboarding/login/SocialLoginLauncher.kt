package com.dallim.app.onboarding.login

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.dallim.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction over the Google Sign-In flow so [LoginViewModel] doesn't depend on the SDK
 * directly. [RealSocialLoginLauncher] is the production implementation (Credential Manager);
 * [NoOpSocialLoginLauncher] remains as a dependency-free stand-in.
 *
 * Kakao는 제외하기로 결정되어 이 인터페이스에 연동하지 않는다 (백엔드 KakaoAuthClient/AuthRoutes는
 * 이미 완성돼 있고 나중에 재사용한다 — 이번 라운드는 Google부터 먼저 완성하는 것뿐).
 */
interface SocialLoginLauncher {
    /**
     * Launches Google Sign-In and resolves with a Google ID Token (docs/02-api-spec.md 1장 —
     * `idToken`). [activity] is required by Credential Manager to anchor the system UI.
     */
    suspend fun launchGoogleSignIn(activity: Activity): Result<String>
}

class NoOpSocialLoginLauncher @Inject constructor() : SocialLoginLauncher {
    override suspend fun launchGoogleSignIn(activity: Activity): Result<String> =
        Result.failure(NotImplementedError("Google Sign-In SDK 연동 예정 — 이번 라운드 범위 밖"))
}

/**
 * Real Google Sign-In via Credential Manager (docs/01-feature-spec.md §1.1 S-02).
 * [BuildConfig.GOOGLE_WEB_CLIENT_ID]가 비어있으면(local.properties의 GOOGLE_WEB_CLIENT_ID
 * 미설정) 크래시 대신 명확한 실패를 반환한다. 사용자가 취소하거나 자격 증명이 없는 경우를 포함한
 * 모든 [GetCredentialException]은 조용히 [Result.failure]로 처리한다 — 크래시 금지.
 */
class RealSocialLoginLauncher @Inject constructor() : SocialLoginLauncher {

    override suspend fun launchGoogleSignIn(activity: Activity): Result<String> {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId.isEmpty()) {
            return Result.failure(
                IllegalStateException("GOOGLE_WEB_CLIENT_ID가 설정되지 않았어요"),
            )
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val credentialManager = CredentialManager.create(activity)
            val result = credentialManager.getCredential(activity, request)
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            Result.success(googleIdTokenCredential.idToken)
        } catch (e: GetCredentialException) {
            // Covers user cancellation and every other Credential Manager failure — never crash.
            Result.failure(e)
        } catch (e: GoogleIdTokenParsingException) {
            Result.failure(e)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SocialLoginModule {
    @Binds
    @Singleton
    abstract fun bindSocialLoginLauncher(impl: RealSocialLoginLauncher): SocialLoginLauncher
}
