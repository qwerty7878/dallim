package com.dallim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTypography

/** Route status: DISCOVERY | VERIFIED | POPULAR | UNDER_REVIEW (docs/03-design-system.md §1.3). */
enum class RouteStatus { DISCOVERY, VERIFIED, POPULAR, UNDER_REVIEW }

fun String.toRouteStatus(): RouteStatus = runCatching { RouteStatus.valueOf(this) }.getOrDefault(RouteStatus.DISCOVERY)

@Composable
fun RouteStatusBadge(status: RouteStatus, modifier: Modifier = Modifier) {
    val (bg, fg, label) = when (status) {
        RouteStatus.DISCOVERY -> Triple(DallimColors.RouteDiscovery.copy(alpha = 0.15f), DallimColors.RouteDiscovery, "발견")
        RouteStatus.VERIFIED -> Triple(DallimColors.RouteVerified.copy(alpha = 0.15f), DallimColors.RouteVerified, "검증됨")
        RouteStatus.POPULAR -> Triple(DallimColors.Primary.copy(alpha = 0.12f), DallimColors.Primary, "인기")
        RouteStatus.UNDER_REVIEW -> Triple(DallimColors.RouteUnderReview.copy(alpha = 0.15f), DallimColors.RouteUnderReview, "검토중")
    }
    // "발견"은 아직 아무 판정도 없는 기본 상태라 띄울 이유가 없다 — 나머지는 알아야 할 정보다.
    val tone = if (status == RouteStatus.DISCOVERY) BadgeTone.QUIET else BadgeTone.ACTIVE
    DallimBadge(label = label, foreground = fg, background = bg, modifier = modifier, tone = tone)
}
