package com.dallim.network.auth

import com.dallim.network.common.ApiErrorBody
import com.dallim.network.common.ApiResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import retrofit2.Response as RetrofitResponse

/**
 * Full HTTP round-trips against a local backend weren't reproducible for this fix within a
 * reasonable test window (see the round's report: access tokens are self-contained 2h JWTs not
 * checked against Redis, so wiping the Redis refresh-token store alone doesn't force a 401 on
 * the next call — only an actually-expired/rejected refresh does). This test drives
 * [TokenAuthenticator.authenticate] directly with hand-built OkHttp/Retrofit objects instead of
 * a mocking library (none is on this module's classpath) to pin down exactly the behavior this
 * round changed: a failed refresh must both clear tokens AND publish to [SessionEventBus] — the
 * part that was missing before (see class doc on TokenAuthenticator).
 */
class TokenAuthenticatorTest {

    private val currentAccessToken = "current-access-token"

    private fun request(authHeader: String? = "Bearer $currentAccessToken"): Request =
        Request.Builder()
            .url("http://10.0.2.2:8080/v1/users/me")
            .apply { if (authHeader != null) header("Authorization", authHeader) }
            .build()

    private fun response(request: Request, code: Int = 401): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Unauthorized")
            .build()

    private class FakeTokenProvider(
        accessToken: String?,
        refreshToken: String?,
    ) : TokenProvider {
        private var access = accessToken
        private var refresh = refreshToken
        var clearTokensCallCount = 0
            private set
        var savedAccessToken: String? = null
            private set
        var savedRefreshToken: String? = null
            private set

        override fun getAccessToken(): String? = access
        override fun getRefreshToken(): String? = refresh

        override fun saveTokens(accessToken: String, refreshToken: String) {
            access = accessToken
            refresh = refreshToken
            savedAccessToken = accessToken
            savedRefreshToken = refreshToken
        }

        override fun clearTokens() {
            clearTokensCallCount++
            access = null
            refresh = null
        }
    }

    private class FakeRefreshApi(
        private val result: RetrofitResponse<ApiResponse<RefreshResponseBody>>,
    ) : RefreshApi {
        var callCount = 0
            private set

        override suspend fun refresh(request: RefreshRequest): RetrofitResponse<ApiResponse<RefreshResponseBody>> {
            callCount++
            return result
        }
    }

    /**
     * `SessionEventBus`'s `MutableSharedFlow(extraBufferCapacity = 1)` has `replay = 0` (matches
     * this round's spec exactly) — the buffered slot only serves a collector that is *already
     * subscribed* at the moment `tryEmit` runs (exactly how `DallimNavHost`'s
     * `LaunchedEffect(Unit) { sessionExpired.collect { ... } }` subscribes once at composition
     * and then waits); a collector that starts *after* the emit sees nothing. So this helper
     * subscribes first (waiting on `subscriptionCount` for a real synchronization point, not a
     * fixed sleep), only then runs [action] (which synchronously `tryEmit`s inside
     * `authenticate()`), and reports whether that already-subscribed collector received it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun SessionEventBus.didEmitDuring(action: () -> Unit): Boolean {
        val received = AtomicBoolean(false)
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Default + job)
        scope.launch { sessionExpired.collect { received.set(true) } }
        // `sessionExpired` is statically a read-only SharedFlow (production hides tryEmit/
        // subscriptionCount on purpose), but the runtime instance is still the MutableSharedFlow
        // SessionEventBus built — cast back just to observe subscriptionCount as a
        // synchronization point (bumped synchronously the moment collect() registers, before it
        // ever suspends waiting for a value), so this doesn't race against a fixed sleep.
        @Suppress("UNCHECKED_CAST")
        val subscriptionCount = (sessionExpired as MutableSharedFlow<Unit>).subscriptionCount
        runBlocking<Unit> {
            withTimeout(2_000L) {
                subscriptionCount.first { count: Int -> count > 0 }
            }
        }

        action()

        runBlocking { delay(200) } // let the Dispatchers.Default collector actually process it
        job.cancel()
        return received.get()
    }

    @Test
    fun `refresh failing with REFRESH_TOKEN_EXPIRED_OR_INVALID clears tokens and notifies SessionEventBus`() {
        val tokenProvider = FakeTokenProvider(accessToken = currentAccessToken, refreshToken = "dead-refresh-token")
        val failure = RetrofitResponse.success(
            ApiResponse<RefreshResponseBody>(
                success = false,
                error = ApiErrorBody(code = "REFRESH_TOKEN_EXPIRED_OR_INVALID", message = "만료된 리프레시 토큰입니다"),
            ),
        )
        val refreshApi = FakeRefreshApi(failure)
        val sessionEventBus = SessionEventBus()
        val authenticator = TokenAuthenticator(tokenProvider, { refreshApi }, sessionEventBus)

        var retried: Request? = null
        val notified = sessionEventBus.didEmitDuring {
            retried = authenticator.authenticate(route = null, response = response(request()))
        }

        assertNull("a failed refresh must not hand back a retry request", retried)
        assertEquals(1, refreshApi.callCount)
        assertEquals("clearTokens() must be called exactly once", 1, tokenProvider.clearTokensCallCount)
        assertNull("local tokens must actually be gone", tokenProvider.getAccessToken())
        assertTrue(
            "this is the bug this round fixes: SessionEventBus must be notified so the UI can navigate to login",
            notified,
        )
    }

    @Test
    fun `refresh succeeding saves the rotated tokens, retries the request, and does NOT notify SessionEventBus`() {
        val tokenProvider = FakeTokenProvider(accessToken = currentAccessToken, refreshToken = "still-valid-refresh-token")
        val success = RetrofitResponse.success(
            ApiResponse(
                success = true,
                data = RefreshResponseBody(accessToken = "new-access-token", refreshToken = "rotated-refresh-token"),
            ),
        )
        val refreshApi = FakeRefreshApi(success)
        val sessionEventBus = SessionEventBus()
        val authenticator = TokenAuthenticator(tokenProvider, { refreshApi }, sessionEventBus)

        var retried: Request? = null
        val notified = sessionEventBus.didEmitDuring {
            retried = authenticator.authenticate(route = null, response = response(request()))
        }

        assertEquals("Bearer new-access-token", retried?.header("Authorization"))
        assertEquals(0, tokenProvider.clearTokensCallCount)
        assertEquals("new-access-token", tokenProvider.savedAccessToken)
        assertEquals("rotated-refresh-token", tokenProvider.savedRefreshToken)
        assertFalse(
            "a successful refresh is not a session expiry — must not bounce the user to login",
            notified,
        )
    }

    @Test
    fun `no locally saved refresh token short-circuits without touching SessionEventBus`() {
        // The pre-login edge case called out in this round's instructions: there was never a
        // session to bounce the user out of, so this must NOT fire SessionEventBus.
        val tokenProvider = FakeTokenProvider(accessToken = null, refreshToken = null)
        val refreshApi = FakeRefreshApi(RetrofitResponse.success(ApiResponse<RefreshResponseBody>(success = false)))
        val sessionEventBus = SessionEventBus()
        val authenticator = TokenAuthenticator(tokenProvider, { refreshApi }, sessionEventBus)

        var retried: Request? = null
        val notified = sessionEventBus.didEmitDuring {
            retried = authenticator.authenticate(route = null, response = response(request(authHeader = null)))
        }

        assertNull(retried)
        assertEquals(0, refreshApi.callCount)
        assertEquals(0, tokenProvider.clearTokensCallCount)
        assertFalse(notified)
    }

    @Test
    fun `gives up after two prior responses to avoid an infinite retry loop`() {
        val tokenProvider = FakeTokenProvider(accessToken = currentAccessToken, refreshToken = "some-refresh-token")
        val refreshApi = FakeRefreshApi(RetrofitResponse.success(ApiResponse<RefreshResponseBody>(success = false)))
        val sessionEventBus = SessionEventBus()
        val authenticator = TokenAuthenticator(tokenProvider, { refreshApi }, sessionEventBus)

        val first = response(request(), code = 401)
        val second = response(request(), code = 401).newBuilder().priorResponse(first).build()

        val retried = authenticator.authenticate(route = null, response = second)

        assertNull(retried)
        assertFalse("must not even attempt another refresh once the retry budget is spent", refreshApi.callCount > 0)
    }
}
