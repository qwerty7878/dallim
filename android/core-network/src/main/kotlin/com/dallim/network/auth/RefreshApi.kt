package com.dallim.network.auth

import com.dallim.network.common.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * POST /auth/refresh (docs/02-api-spec.md 1장) — deliberately its own tiny Retrofit interface,
 * built on a plain OkHttpClient with NO AuthInterceptor/TokenAuthenticator attached
 * (see NetworkModule.provideRefreshRetrofit), so refreshing a token can never itself trigger
 * another 401 -> refresh loop.
 */
interface RefreshApi {
    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<ApiResponse<RefreshResponseBody>>
}

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class RefreshResponseBody(val accessToken: String, val refreshToken: String)
