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
 * S-90/S-91 러닝 커리어(완주 이력) 배지 — [MeetupStatusBadge]와 같은 pill 스타일.
 *
 * 완주 이력은 자기신고(self-report)이며 서버가 항상 `verified: false`를 내려준다(인증 승격
 * 경로 없음, docs/02-api-spec.md 15장). "인증됨"으로 오해할 수 있는 표현을 절대 쓰지 않기
 * 위해 이 배지를 항상 눈에 띄게 둔다.
 */
@Composable
fun UnverifiedBadge(modifier: Modifier = Modifier) {
    Text(
        text = "미인증",
        style = DallimTypography.Caption,
        color = DallimColors.TextSecondary,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DallimColors.TextSecondary.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** `isPb`(서버가 계산한 종목별 개인 최고 기록) 강조 배지. */
@Composable
fun PbBadge(modifier: Modifier = Modifier) {
    Text(
        text = "PB",
        style = DallimTypography.Caption,
        color = DallimColors.Primary,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DallimColors.PrimaryLight)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
