package com.dallim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTypography

/**
 * S-47/S-49 같이 달리기 모집 상태 배지 (docs/01-feature-spec.md 1.8.2).
 *
 * 우선순위는 발생 조건이 아니라 "사용자가 알아야 할 가장 구체적인 사실" 기준이다: 호스트가
 * 모집을 취소했다는 사실([CANCELLED])은 시간이 지나 자동으로 "종료" 상태가 된 것보다 더 중요한
 * 정보라서 시간과 무관하게 항상 앞선다 — docs/01-feature-spec.md 1.8.2 "취소된 모집은 ...
 * 목록에서 '취소됨'으로 **계속** 보이게 한다" 참고. [NONE]이면 정상 표시(탭 가능).
 */
enum class MeetupBadgeState { NONE, CANCELLED, ENDED, FULL }

fun meetupBadgeState(status: String, isFull: Boolean, isPast: Boolean): MeetupBadgeState = when {
    status == "CANCELLED" -> MeetupBadgeState.CANCELLED
    isPast -> MeetupBadgeState.ENDED
    isFull -> MeetupBadgeState.FULL
    else -> MeetupBadgeState.NONE
}

@Composable
fun MeetupStatusBadge(state: MeetupBadgeState, modifier: Modifier = Modifier) {
    if (state == MeetupBadgeState.NONE) return

    val (bg, fg, label) = when (state) {
        MeetupBadgeState.CANCELLED -> Triple(DallimColors.Error.copy(alpha = 0.12f), DallimColors.Error, "취소됨")
        MeetupBadgeState.ENDED -> Triple(DallimColors.TextSecondary.copy(alpha = 0.12f), DallimColors.TextSecondary, "종료")
        MeetupBadgeState.FULL -> Triple(DallimColors.Warning.copy(alpha = 0.15f), DallimColors.Warning, "마감")
        MeetupBadgeState.NONE -> return
    }
    // 이 배지는 취소됨/종료/마감 셋뿐 — 전부 "지금은 참가할 수 없다"는 뜻이라 모두 가라앉힌다.
    // 참가 가능한 모집은 [MeetupBadgeState.NONE]이라 애초에 배지를 그리지 않는다.
    DallimBadge(label = label, foreground = fg, background = bg, modifier = modifier, tone = BadgeTone.QUIET)
}
