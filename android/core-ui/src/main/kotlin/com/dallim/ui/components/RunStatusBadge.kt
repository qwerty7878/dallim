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
 * Run(러닝 세션) 판정 상태 — `COMPLETED` | `PARTIAL` | `ABORTED` | `UNDER_REVIEW`
 * (docs/02-api-spec.md 5장 `POST /runs/{id}/finish`). [RouteStatusBadge]와는 다른 도메인
 * (Route 상태 DISCOVERY/VERIFIED/POPULAR와 값 집합이 겹치지 않는다) — S-25 결과, S-40/41
 * 달림북에서 재사용한다.
 */
enum class RunStatus { COMPLETED, PARTIAL, ABORTED, UNDER_REVIEW }

fun String.toRunStatus(): RunStatus = runCatching { RunStatus.valueOf(this) }.getOrDefault(RunStatus.PARTIAL)

@Composable
fun RunStatusBadge(status: RunStatus, modifier: Modifier = Modifier) {
    val (fg, label) = when (status) {
        RunStatus.COMPLETED -> DallimColors.Success to "완주"
        RunStatus.PARTIAL -> DallimColors.TextSecondary to "부분 완주"
        RunStatus.ABORTED -> DallimColors.TextSecondary to "중단됨"
        RunStatus.UNDER_REVIEW -> DallimColors.Warning to "검토중"
    }
    Text(
        text = label,
        style = DallimTypography.Caption,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(fg.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
