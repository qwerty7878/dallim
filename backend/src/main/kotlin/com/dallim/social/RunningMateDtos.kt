package com.dallim.social

import kotlinx.serialization.Serializable

@Serializable
data class RunningMateItem(
    val userId: String,
    val nickname: String,
    val avatarId: String?,
    val runTogetherCount: Int,
    val lastRunTogetherAt: String,
)

@Serializable
data class RunningMateListResponse(val items: List<RunningMateItem>)
