package com.dallim.network.user

import com.dallim.network.common.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * docs/02-api-spec.md 18.2 — 유저 차단, 채팅 메시지 발신자 차단으로 스코프를 좁힌다(2026-09-16
 * 신규, v1.3 SPEC 밖). 서버는 차단 관계를 CRUD로만 노출하고 메시지 자체를 필터링하지
 * 않는다 — [com.dallim.app.social.chat.SocialSessionChatViewModel]이 이 목록으로 채팅 화면에서
 * 직접 걸러낸다(순수 클라이언트 사이드 필터). 세션 신청/매칭 등 다른 곳에는 차단 효과가
 * 전파되지 않는다(18.3, 이번 라운드 범위 밖).
 */
interface BlockedUserApi {
    /** 이미 차단한 상대면 그대로 200(멱등). 자기 자신이면 400 VALIDATION_ERROR, 존재하지 않는
     * 유저면 404 BLOCK_TARGET_NOT_FOUND. */
    @POST("users/me/blocks")
    suspend fun blockUser(@Body request: BlockUserRequest): Response<ApiResponse<Unit>>

    /** 차단 관계가 없어도 200(멱등). */
    @DELETE("users/me/blocks/{blockedUserId}")
    suspend fun unblockUser(@Path("blockedUserId") blockedUserId: String): Response<ApiResponse<Unit>>

    /** 최근 차단순, 페이지네이션 없음. */
    @GET("users/me/blocks")
    suspend fun getBlockedUsers(): Response<ApiResponse<BlockedUserListResponseBody>>
}

@Serializable
data class BlockUserRequest(val blockedUserId: String)

/** `gender`는 CLAUDE.md 규칙 2에 따라 어떤 응답에도 없다. */
@Serializable
data class BlockedUserItem(
    val userId: String,
    val nickname: String,
    val avatarId: String?,
    val blockedAt: String,
)

@Serializable
data class BlockedUserListResponseBody(val items: List<BlockedUserItem>)
