package com.dallim.network.meetup

import com.dallim.network.common.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * docs/02-api-spec.md 14장 — 같이 달리기 모집 (S-47/48/49, docs/01-feature-spec.md 1.8).
 * `GET /routes/{routeId}/meetups`만 인증 불필요, 나머지는 전부 🔒 (backend MeetupRoutes.kt와 동일).
 */
interface MeetupApi {
    /** S-47 모집 목록 — `scheduledAt` 오름차순, 페이지네이션 없음 (14.2). 인증 불필요. */
    @GET("routes/{routeId}/meetups")
    suspend fun getMeetups(@Path("routeId") routeId: String): Response<ApiResponse<MeetupListResponseBody>>

    /** S-48 모집 생성 (14.3). */
    @POST("routes/{routeId}/meetups")
    suspend fun createMeetup(
        @Path("routeId") routeId: String,
        @Body request: CreateMeetupRequest,
    ): Response<ApiResponse<CreateMeetupResponseBody>>

    /** S-49 모집 상세 (14.4). `isHost`/`isJoined`는 요청 유저 기준 서버 계산값. */
    @GET("meetups/{meetupId}")
    suspend fun getMeetupDetail(@Path("meetupId") meetupId: String): Response<ApiResponse<MeetupDetailResponseBody>>

    /** 409 ALREADY_JOINED / MEETUP_FULL / MEETUP_ENDED (14.4). */
    @POST("meetups/{meetupId}/join")
    suspend fun joinMeetup(@Path("meetupId") meetupId: String): Response<ApiResponse<Unit>>

    /** host가 호출하면 400 VALIDATION_ERROR, 참가 중이 아니어도 멱등 200 (14.4). */
    @POST("meetups/{meetupId}/leave")
    suspend fun leaveMeetup(@Path("meetupId") meetupId: String): Response<ApiResponse<Unit>>

    /** host가 아니면 403 MEETUP_NOT_HOST. soft-cancel(status: CANCELLED)만 하고 실제 삭제는 안 함 (14.4). */
    @DELETE("meetups/{meetupId}")
    suspend fun cancelMeetup(@Path("meetupId") meetupId: String): Response<ApiResponse<Unit>>
}

@Serializable
data class CreateMeetupRequest(
    val scheduledAt: String,
    val maxParticipants: Int,
    val description: String? = null,
)

@Serializable
data class CreateMeetupResponseBody(val meetupId: String)

/** GET /routes/{routeId}/meetups 아이템 (14.2). `status`는 "OPEN" | "CANCELLED". */
@Serializable
data class MeetupListItem(
    val meetupId: String,
    val hostNickname: String,
    val scheduledAt: String,
    val maxParticipants: Int,
    val currentParticipants: Int,
    val status: String,
    val isFull: Boolean,
    val isPast: Boolean,
)

@Serializable
data class MeetupListResponseBody(val items: List<MeetupListItem>)

@Serializable
data class MeetupParticipantItem(
    val userId: String,
    val nickname: String,
    val isHost: Boolean,
)

/** GET /meetups/{meetupId} (14.4). */
@Serializable
data class MeetupDetailResponseBody(
    val meetupId: String,
    val routeId: String,
    val routeName: String,
    val hostUserId: String,
    val hostNickname: String,
    val scheduledAt: String,
    val maxParticipants: Int,
    val description: String?,
    val status: String,
    val isFull: Boolean,
    val isPast: Boolean,
    val isHost: Boolean,
    val isJoined: Boolean,
    val participants: List<MeetupParticipantItem>,
)

/** "OPEN" | "CANCELLED" — 목록/상세 배지 판단에 쓰는 상수 (Meetup.MeetupStatus 서버 enum과 동일). */
const val MEETUP_STATUS_CANCELLED = "CANCELLED"
