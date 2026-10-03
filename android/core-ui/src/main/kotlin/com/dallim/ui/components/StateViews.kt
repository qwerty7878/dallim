package com.dallim.ui.components

import com.dallim.ui.icons.DallimIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/** Centered loading state — one of the three screens allowed to center-align (guide §5). */
@Composable
fun DallimLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = DallimColors.Primary, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
    }
}

/** 상태 화면 공통 — 옅은 회색 원 안의 아이콘 + 제목 + 설명. 글자만 덩그러니 있던 빈/오류 화면을 대체한다. */
@Composable
private fun StateBody(
    icon: ImageVector,
    title: String,
    description: String?,
    modifier: Modifier,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(DallimColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = DallimColors.TextTertiary, modifier = Modifier.size(28.dp))
        }
        Text(
            text = title,
            style = DallimTypography.Title3,
            color = DallimColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.md),
        )
        if (description != null) {
            Text(
                text = description,
                style = DallimTypography.Body,
                color = DallimColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        actions()
    }
}

/**
 * Centered empty state — one of the three screens allowed to center-align (guide §5).
 * [actionText]/[onActionClick] are optional so callers that need a CTA (e.g. S-17 "아직 저장한
 * 코스가 없어요" -> "코스 탐색하기") don't have to hand-roll their own empty-state layout.
 */
@Composable
fun DallimEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    icon: ImageVector = DallimIcons.Inbox,
) {
    StateBody(icon, title, description, modifier) {
        if (actionText != null && onActionClick != null) {
            DallimPrimaryButton(text = actionText, onClick = onActionClick, modifier = Modifier.padding(top = Spacing.lg))
        }
    }
}

/**
 * Centered error state — same slot as [DallimLoadingState]/[DallimEmptyState] for a failed
 * `safeApiCall` (`com.dallim.app.common.UiResult.Error`). [onRetry] is optional so a screen can
 * omit it when a retry wouldn't make sense.
 */
@Composable
fun DallimErrorState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    StateBody(DallimIcons.CircleAlert, title, description, modifier) {
        if (onRetry != null) {
            DallimTextButton(text = "다시 시도", onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
        }
    }
}
