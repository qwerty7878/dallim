package com.dallim.common

import kotlinx.serialization.Serializable

/**
 * Common response envelope for every endpoint in docs/02-api-spec.md 0장.
 *
 * Success:
 * { "success": true, "data": { ... }, "error": null }
 *
 * Failure:
 * { "success": false, "data": null, "error": { "code": "...", "message": "..." } }
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiErrorBody? = null,
) {
    companion object {
        fun <T> success(data: T? = null): ApiResponse<T> = ApiResponse(success = true, data = data, error = null)

        fun <T> error(code: String, message: String): ApiResponse<T> =
            ApiResponse(success = false, data = null, error = ApiErrorBody(code = code, message = message))

        fun <T> error(error: ApiErrorBody): ApiResponse<T> =
            ApiResponse(success = false, data = null, error = error)
    }
}

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
)
