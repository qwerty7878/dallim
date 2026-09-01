package com.dallim.network.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * On a 401 response, calls POST /auth/refresh once and retries the original request with the
 * new access token — the flow described in docs/02-api-spec.md 0/1장 and CLAUDE.md's Android
 * scope ("401 시 Refresh 재시도"). Refresh Token rotation means every successful refresh
 * invalidates the previous refresh token server-side, so we must always persist the *new*
 * refreshToken from the response, not just the accessToken.
 *
 * `refreshApiProvider` is a Provider (not a direct RefreshApi) to break the Hilt dependency
 * cycle: NetworkModule needs this Authenticator to build the *main* OkHttpClient, but the
 * RefreshApi is built from its own separate OkHttpClient (see RefreshApi doc) — Provider defers
 * resolution until authenticate() actually runs.
 *
 * OkHttp calls Authenticator.authenticate() synchronously off a background dispatcher thread,
 * so a blocking runBlocking call here is intended (not on the main thread).
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenProvider: TokenProvider,
    private val refreshApiProvider: Provider<RefreshApi>,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Avoid infinite retry loops if the refreshed token is *still* rejected.
        if (responseCount(response) >= 2) return null

        val refreshToken = tokenProvider.getRefreshToken() ?: return null

        synchronized(lock) {
            // Another thread may have already refreshed while we waited for the lock —
            // if the access token attached to this request differs from the current one,
            // just retry with the current token instead of refreshing again.
            val currentAccessToken = tokenProvider.getAccessToken()
            val requestAccessToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (currentAccessToken != null && currentAccessToken != requestAccessToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccessToken")
                    .build()
            }

            val newTokens = runCatching {
                runBlocking { refreshApiProvider.get().refresh(RefreshRequest(refreshToken)) }
            }.getOrNull()

            val body = newTokens?.body()
            if (newTokens?.isSuccessful != true || body?.success != true || body.data == null) {
                // REFRESH_TOKEN_EXPIRED_OR_INVALID (docs/02-api-spec.md 1장) — force re-login.
                tokenProvider.clearTokens()
                return null
            }

            val (accessToken, rotatedRefreshToken) = body.data
            tokenProvider.saveTokens(accessToken, rotatedRefreshToken)

            return response.request.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
