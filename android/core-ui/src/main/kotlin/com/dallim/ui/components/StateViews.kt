package com.dallim.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
        CircularProgressIndicator(color = DallimColors.Primary)
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
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = DallimTypography.Body, color = DallimColors.TextPrimary, textAlign = TextAlign.Center)
        if (description != null) {
            Text(
                text = description,
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
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
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = DallimTypography.Body, color = DallimColors.TextPrimary, textAlign = TextAlign.Center)
        if (description != null) {
            Text(
                text = description,
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        if (onRetry != null) {
            DallimTextButton(text = "다시 시도", onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
        }
    }
}
