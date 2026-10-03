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
 * S-30/S-32 소셜 세션 상태 배지 — 서버가 매 조회 시 계산해 내려주는 5개 값
 * (docs/02-api-spec.md 17.1/17.3, backend `SocialSessionDisplayStatus`) 그대로 매핑한다.
 * [MeetupStatusBadge]와 달리 [RECRUITING]도 화면에 보여야 해서(모집 중임을 알리는 정보) NONE이
 * 없다 — 값 하나마다 항상 뱃지를 그린다. [CLOSED]는 2026-09-16 신규 — 호스트가 취소한
 * [CANCELLED]와 달리 "시간이 지나 자동으로 마감된" 상태라 색을 다르게(회색) 준다.
 */
enum class SocialSessionBadgeState { RECRUITING, NEAR_CONFIRMATION, CONFIRMED, CLOSED, CANCELLED }

fun socialSessionBadgeState(status: String): SocialSessionBadgeState = when (status) {
    "NEAR_CONFIRMATION" -> SocialSessionBadgeState.NEAR_CONFIRMATION
    "CONFIRMED" -> SocialSessionBadgeState.CONFIRMED
    "CLOSED" -> SocialSessionBadgeState.CLOSED
    "CANCELLED" -> SocialSessionBadgeState.CANCELLED
    else -> SocialSessionBadgeState.RECRUITING
}

@Composable
fun SocialSessionStatusBadge(state: SocialSessionBadgeState, modifier: Modifier = Modifier) {
    val (bg, fg, label) = when (state) {
        SocialSessionBadgeState.RECRUITING ->
            Triple(DallimColors.PrimaryLight, DallimColors.Primary, "모집 중")
        SocialSessionBadgeState.NEAR_CONFIRMATION ->
            Triple(DallimColors.Warning.copy(alpha = 0.15f), DallimColors.Warning, "성사 임박")
        SocialSessionBadgeState.CONFIRMED ->
            Triple(DallimColors.Success.copy(alpha = 0.15f), DallimColors.Success, "성사됨")
        SocialSessionBadgeState.CLOSED ->
            Triple(DallimColors.Border, DallimColors.TextSecondary, "마감")
        SocialSessionBadgeState.CANCELLED ->
            Triple(DallimColors.Error.copy(alpha = 0.12f), DallimColors.Error, "취소됨")
    }
    DallimBadge(label = label, foreground = fg, background = bg, modifier = modifier)
}
