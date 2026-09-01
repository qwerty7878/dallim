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
        RouteStatus.DISCOVERY -> Triple(DallimColors.RouteDiscovery.copy(alpha = 0.15f), DallimColors.RouteDiscovery, "DISCOVERY")
        RouteStatus.VERIFIED -> Triple(DallimColors.RouteVerified.copy(alpha = 0.15f), DallimColors.RouteVerified, "VERIFIED")
        RouteStatus.POPULAR -> Triple(DallimColors.Primary.copy(alpha = 0.12f), DallimColors.Primary, "POPULAR")
        RouteStatus.UNDER_REVIEW -> Triple(DallimColors.RouteUnderReview.copy(alpha = 0.15f), DallimColors.RouteUnderReview, "검토중")
    }
    Text(
        text = label,
        style = DallimTypography.Caption,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
