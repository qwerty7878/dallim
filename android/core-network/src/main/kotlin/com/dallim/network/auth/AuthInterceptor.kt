package com.dallim.network.auth

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches `Authorization: Bearer {accessToken}` to every outgoing request that has one
 * (docs/02-api-spec.md 0장). Endpoints that don't require auth simply ignore the header
 * server-side, so this doesn't need to know which routes are 🔒 — it's harmless to attach
 * a token to a public GET /routes call, for example.
 *
 * auth/login, auth/signup, auth/google and auth/refresh are called before any
 * token exists (or via the separate refresh-only client — see RefreshApi), so there's nothing
 * to attach for them; getAccessToken() simply returns null pre-login.
 */
class AuthInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val accessToken = tokenProvider.getAccessToken()

        val request = if (accessToken != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        } else {
            original
        }

        return chain.proceed(request)
    }
}
