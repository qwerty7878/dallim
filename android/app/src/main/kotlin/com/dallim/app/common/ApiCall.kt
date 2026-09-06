package com.dallim.app.common

import com.dallim.network.common.ApiErrorBody
import com.dallim.network.common.ApiResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Response

/** Uniform outcome for a screen-level API call — success payload, or a message to show. */
sealed interface UiResult<out T> {
    data object Loading : UiResult<Nothing>
    data class Success<T>(val data: T) : UiResult<T>
    data class Error(val message: String, val code: String? = null) : UiResult<Nothing>
}

/** Only the `error` half of [ApiResponse] — enough to decode a non-2xx body without knowing `T`. */
@Serializable
private data class ApiErrorEnvelope(val error: ApiErrorBody? = null)

private val errorEnvelopeJson = Json { ignoreUnknownKeys = true }

/**
 * Unwraps a Retrofit `Response<ApiResponse<T>>` (docs/02-api-spec.md 0장 공통 응답 래퍼) into a
 * [UiResult], collapsing the HTTP-layer / envelope-layer / network-exception cases every screen
 * ViewModel would otherwise repeat.
 *
 * Retrofit only runs the body converter on a *successful* HTTP response — for a non-2xx response
 * `response.body()` is always null (the raw body sits in `errorBody()` instead), so `error.code`
 * (needed to branch UI behavior on a specific backend error, e.g. `DISCOVERY_QUOTA_EXCEEDED`) has
 * to be parsed off `errorBody()` by hand here rather than read off `response.body()`.
 */
suspend fun <T> safeApiCall(block: suspend () -> Response<ApiResponse<T>>): UiResult<T> = try {
    val response = block()
    if (response.isSuccessful) {
        val body = response.body()
        if (body?.success == true) {
            @Suppress("UNCHECKED_CAST")
            UiResult.Success(body.data as T)
        } else {
            UiResult.Error(
                message = body?.error?.message ?: "요청을 처리하지 못했어요. (${response.code()})",
                code = body?.error?.code,
            )
        }
    } else {
        val parsedError = response.errorBody()?.string()?.let { raw ->
            runCatching { errorEnvelopeJson.decodeFromString(ApiErrorEnvelope.serializer(), raw) }.getOrNull()
        }?.error
        UiResult.Error(
            message = parsedError?.message ?: "요청을 처리하지 못했어요. (${response.code()})",
            code = parsedError?.code,
        )
    }
} catch (e: java.io.IOException) {
    UiResult.Error(message = "네트워크 연결을 확인해주세요.")
} catch (e: Exception) {
    UiResult.Error(message = e.message ?: "알 수 없는 오류가 발생했어요.")
}
