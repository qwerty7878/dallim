package com.dallim.meetup

import kotlinx.serialization.Serializable

// Request/response DTOs — docs/02-api-spec.md 14장. gender is never included anywhere here
// (CLAUDE.md rule 2) -- participant entries only ever carry userId/nickname/isHost.

@Serializable
data class CreateMeetupRequest(
    val scheduledAt: String,
    val maxParticipants: Int,
    val description: String? = null,
)

@Serializable
data class CreateMeetupResponse(val meetupId: String)

/** GET /routes/{routeId}/meetups item — 14.2. */
@Serializable
data class MeetupListItem(
    val meetupId: String,
    val hostNickname: String,
    val scheduledAt: String,
    val maxParticipants: Int,
    val currentParticipants: Int,
    val status: MeetupStatus,
    val isFull: Boolean,
    val isPast: Boolean,
)

@Serializable
data class MeetupListResponse(val items: List<MeetupListItem>)

@Serializable
data class MeetupParticipantItem(
    val userId: String,
    val nickname: String,
    val isHost: Boolean,
)

/** GET /meetups/{meetupId} — 14.4. */
@Serializable
data class MeetupDetailResponse(
    val meetupId: String,
    val routeId: String,
    val routeName: String,
    val hostUserId: String,
    val hostNickname: String,
    val scheduledAt: String,
    val maxParticipants: Int,
    val description: String?,
    val status: MeetupStatus,
    val isFull: Boolean,
    val isPast: Boolean,
    val isHost: Boolean,
    val isJoined: Boolean,
    val participants: List<MeetupParticipantItem>,
)
