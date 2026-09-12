package com.dallim.social

import kotlinx.serialization.Serializable

// Request/response DTOs — docs/02-api-spec.md 17장 이어서 (S-38). gender는 여기에도 없다
// (CLAUDE.md 규칙 2).

@Serializable
data class FeedbackTargetItem(
    val userId: String,
    val nickname: String,
    val avatarId: String?,
    val isHost: Boolean,
)

@Serializable
data class FeedbackTargetsResponse(val items: List<FeedbackTargetItem>)

/** [tags]는 SocialFeedbackTags.ALLOWED에서 최대 3개, 미선택(빈 배열)도 허용. */
@Serializable
data class SubmitFeedbackRequest(
    val targetUserId: String,
    val tags: List<String> = emptyList(),
    val wantsToRunAgain: Boolean = false,
)

@Serializable
data class SubmitFeedbackResult(val mateEstablished: Boolean)
