package com.dallim.network.common

import kotlinx.serialization.Serializable

/**
 * Client-side mirror of backend's com.dallim.common.ApiResponse (docs/02-api-spec.md 0장).
 * Every Retrofit service method should return ApiResponse<T>, not a bare T, so callers can
 * branch on `success`/`error` uniformly instead of relying on HTTP status alone.
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiErrorBody? = null,
)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
)
