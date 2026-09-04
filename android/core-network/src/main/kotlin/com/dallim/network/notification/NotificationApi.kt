package com.dallim.network.notification

import com.dallim.network.common.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * docs/02-api-spec.md 9장 — S-46 인앱 알림함 (1단계). 트리거는 러닝 완주(`RunStatus.COMPLETED`)
 * 1건만 서버가 자동 생성하며(9.4), 폰 시스템 푸시(FCM)와 알림 설정 화면은 이번 범위 밖이다.
 */
interface NotificationApi {
    /** S-46 알림 목록(최신순). `GET /routes` 페이지네이션과 동일 관례(page 기본 0, size 기본 20). */
    @GET("notifications")
    suspend fun getNotifications(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Response<ApiResponse<NotificationListResponseBody>>

    /** S-10 종 아이콘 배지용 경량 엔드포인트 — 목록 전체를 받지 않아도 된다 (docs/02-api-spec.md 9.2). */
    @GET("notifications/unread-count")
    suspend fun getUnreadCount(): Response<ApiResponse<UnreadCountResponseBody>>

    /** 멱등 — 이미 읽음이어도 200, 본인 알림이 아니면 404 (docs/02-api-spec.md 9.3). */
    @POST("notifications/{id}/read")
    suspend fun markAsRead(@Path("id") notificationId: String): Response<ApiResponse<Unit>>
}

@Serializable
data class NotificationItem(
    val id: String,
    /** 이번 라운드에는 `"RUN_COMPLETED"` 값만 내려온다 (docs/02-api-spec.md 9.4). */
    val type: String,
    val title: String,
    val body: String,
    val relatedRunId: String? = null,
    val isRead: Boolean,
    val createdAt: String,
)

@Serializable
data class NotificationListResponseBody(
    val items: List<NotificationItem>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
)

@Serializable
data class UnreadCountResponseBody(val unreadCount: Int)
