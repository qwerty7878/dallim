package com.dallim.network.auth

/**
 * Reads/writes the JWT pair used by AuthInterceptor / TokenAuthenticator.
 *
 * Intentionally a blocking (non-suspend) interface: OkHttp's Interceptor.intercept and
 * Authenticator.authenticate both run synchronously on an OkHttp dispatcher thread, not in a
 * coroutine, so they cannot `await` a suspend DataStore read. DataStoreTokenProvider bridges
 * this with `runBlocking { flow.first() }` — acceptable for MVP1 (single small read), but if
 * profiling later shows contention, swap in an in-memory cache kept in sync with DataStore.
 */
interface TokenProvider {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?

    /** Persists a newly (re)issued token pair — called after login and after a successful refresh. */
    fun saveTokens(accessToken: String, refreshToken: String)

    /** Called when refresh itself fails (401 REFRESH_TOKEN_EXPIRED_OR_INVALID) — caller must force re-login. */
    fun clearTokens()
}
