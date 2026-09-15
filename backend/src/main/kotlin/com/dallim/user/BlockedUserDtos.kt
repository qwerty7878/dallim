package com.dallim.user

import kotlinx.serialization.Serializable

// POST/DELETE/GET /users/me/blocks — docs/02-api-spec.md 18장. gender는 어떤 응답에도 없다
// (CLAUDE.md 규칙 2).

/** POST /users/me/blocks request body. */
@Serializable
data class BlockUserRequest(val blockedUserId: String)

@Serializable
data class BlockedUserItem(
    val userId: String,
    val nickname: String,
    val avatarId: String?,
    val blockedAt: String,
)

@Serializable
data class BlockedUsersResponse(val items: List<BlockedUserItem>)
