package com.dallim.app.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dallim.app.onboarding.profile.avatarOptions
import com.dallim.ui.theme.DallimColors

/**
 * 호스트/참가자/신청자 아바타 — [com.dallim.app.my.MyScreen]의 `AvatarBadge`와 동일한 관례
 * (avatarId는 실물 이미지 에셋이 없는 문자열 ID라 S-04 6종 벡터 아이콘으로 표시,
 * docs/04-ui-guide.md §7 이모지 금지 원칙). 소셜 세션 응답의 `hostAvatarId`/`avatarId`는
 * nullable이라(아직 프로필에서 고르지 않은 유저) null이면 첫 옵션으로 폴백한다.
 */
@Composable
fun SocialAvatarBadge(avatarId: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val avatar = avatarOptions.firstOrNull { it.id == avatarId } ?: avatarOptions.first()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(DallimColors.PrimaryLight),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = avatar.icon,
            contentDescription = null,
            tint = DallimColors.Primary,
            modifier = Modifier.size(size / 2),
        )
    }
}
