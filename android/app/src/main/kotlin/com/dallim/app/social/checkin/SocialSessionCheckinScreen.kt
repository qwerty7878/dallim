package com.dallim.app.social.checkin

import com.dallim.ui.icons.DallimIcons
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.social.SocialAvatarBadge
import com.dallim.app.social.SocialSessionFormat
import com.dallim.network.social.SocialSessionCheckinStatus
import com.dallim.network.social.SocialSessionReadyCheckItem
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimSecondaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.components.DallimSnackbar
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-36 GPS 체크인 + S-37 Ready Check (docs/02-api-spec.md 17.14/17.15) 한 화면. 상단에 내
 * 체크인 액션, 아래에 전원의 체크인 현황판. 호스트에게만 "달리기 시작"/"수동 확인"이 보인다.
 * `started == true`가 되면 S-38(평가) 진입 CTA를 노출한다(작업 브리핑 지시 — 별도 "세션 종료"
 * 액션이 없어 이 값으로 근사).
 */
@Composable
fun SocialSessionCheckinRoute(
    onBackClick: () -> Unit,
    onFeedbackClick: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SocialSessionCheckinViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) viewModel.onCheckinClick()
    }

    SocialSessionCheckinScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetryClick = viewModel::load,
        onCheckinClick = {
            if (hasForegroundLocationPermission(context)) {
                viewModel.onCheckinClick()
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            }
        },
        onStartClick = viewModel::onStartClick,
        onManualConfirmClick = viewModel::onManualConfirmClick,
        onFeedbackClick = { onFeedbackClick(viewModel.sessionId) },
        onToastMessageShown = viewModel::onToastMessageShown,
        modifier = modifier,
    )
}

private fun hasForegroundLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
private fun SocialSessionCheckinScreen(
    uiState: SocialSessionCheckinUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onCheckinClick: () -> Unit,
    onStartClick: () -> Unit,
    onManualConfirmClick: (String) -> Unit,
    onFeedbackClick: () -> Unit,
    onToastMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val toastMessage = (uiState as? SocialSessionCheckinUiState.Success)?.checkinToastMessage

    LaunchedEffect(toastMessage) {
        val message = toastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onToastMessageShown()
    }

    Scaffold(
        modifier = modifier,
        containerColor = DallimColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) { DallimSnackbar(it) } },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DallimColors.Background)
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = DallimIcons.ArrowLeft, contentDescription = "뒤로가기", tint = DallimColors.TextPrimary)
                }
                Text(text = "체크인 · Ready Check", style = DallimTypography.Title2, color = DallimColors.TextPrimary)
            }

            when (uiState) {
                SocialSessionCheckinUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
                is SocialSessionCheckinUiState.Error -> DallimErrorState(
                    title = "체크인 정보를 불러오지 못했어요",
                    description = uiState.message,
                    onRetry = onRetryClick,
                    modifier = Modifier.weight(1f),
                )
                is SocialSessionCheckinUiState.Success -> CheckinContent(
                    state = uiState,
                    onCheckinClick = onCheckinClick,
                    onStartClick = onStartClick,
                    onManualConfirmClick = onManualConfirmClick,
                    onFeedbackClick = onFeedbackClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CheckinContent(
    state: SocialSessionCheckinUiState.Success,
    onCheckinClick: () -> Unit,
    onStartClick: () -> Unit,
    onManualConfirmClick: (String) -> Unit,
    onFeedbackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = Spacing.ScreenHorizontal)) {
        val myStatus = state.myStatus
        if (myStatus != null && myStatus.status == SocialSessionCheckinStatus.WAITING && !state.started) {
            DallimPrimaryButton(
                text = if (state.isCheckingIn) "체크인하는 중..." else "체크인하기",
                onClick = onCheckinClick,
                enabled = !state.isCheckingIn,
                modifier = Modifier.padding(top = Spacing.md),
            )
            Text(
                text = "집결지 반경 150m 이내, 집결 30분 전 ~ 15분 후에만 체크인할 수 있어요.",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        } else if (myStatus != null) {
            Text(
                text = statusLabel(myStatus.status) + " · 나",
                style = DallimTypography.Body,
                color = statusColor(myStatus.status),
                modifier = Modifier.padding(top = Spacing.md),
            )
        }

        if (state.isHost && !state.started) {
            DallimSecondaryButton(
                text = if (state.isStarting) "시작하는 중..." else "달리기 시작",
                onClick = onStartClick,
                enabled = !state.isStarting,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        if (state.started) {
            DallimPrimaryButton(
                text = "평가하러 가기",
                onClick = onFeedbackClick,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }

        Text(
            text = "참가자 현황 (${state.items.size})",
            style = DallimTypography.Title2,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm),
        )

        LazyColumn(
            contentPadding = PaddingValues(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(state.items, key = { it.userId }) { item ->
                ReadyCheckRow(
                    item = item,
                    isMe = item.userId == state.myUserId,
                    canManualConfirm = state.isHost && item.userId != state.myUserId && item.status != SocialSessionCheckinStatus.CHECKED_IN && item.status != SocialSessionCheckinStatus.LATE,
                    isConfirming = state.manualConfirmInProgressUserId == item.userId,
                    onManualConfirmClick = { onManualConfirmClick(item.userId) },
                )
            }
        }
    }
}

@Composable
private fun ReadyCheckRow(
    item: SocialSessionReadyCheckItem,
    isMe: Boolean,
    canManualConfirm: Boolean,
    isConfirming: Boolean,
    onManualConfirmClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SocialAvatarBadge(avatarId = item.avatarId, size = 40.dp)
        Column(modifier = Modifier.padding(start = Spacing.sm).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = item.nickname, style = DallimTypography.Body, color = DallimColors.TextPrimary)
                if (item.isHost) {
                    Text(
                        text = " · 호스트",
                        style = DallimTypography.Caption,
                        color = DallimColors.Primary,
                    )
                }
                if (isMe) {
                    Text(text = " · 나", style = DallimTypography.Caption, color = DallimColors.TextSecondary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
                Icon(
                    imageVector = statusIcon(item.status),
                    contentDescription = null,
                    tint = statusColor(item.status),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = statusLabel(item.status) + distanceSuffix(item),
                    style = DallimTypography.Caption,
                    color = statusColor(item.status),
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
        if (canManualConfirm) {
            DallimTextButton(
                text = if (isConfirming) "확인 중..." else "수동 확인",
                onClick = onManualConfirmClick,
                enabled = !isConfirming,
            )
        }
    }
}

private fun distanceSuffix(item: SocialSessionReadyCheckItem): String {
    val distance = item.distanceErrorM ?: return ""
    return " · 집결지까지 ${"%.0f".format(distance)}m"
}

private fun statusLabel(status: String): String = when (status) {
    SocialSessionCheckinStatus.CHECKED_IN -> "체크인 완료"
    SocialSessionCheckinStatus.LATE -> "지각 체크인"
    SocialSessionCheckinStatus.NO_SHOW -> "노쇼"
    else -> "대기 중"
}

private fun statusColor(status: String): Color = when (status) {
    SocialSessionCheckinStatus.CHECKED_IN -> DallimColors.Success
    SocialSessionCheckinStatus.LATE -> DallimColors.Warning
    SocialSessionCheckinStatus.NO_SHOW -> DallimColors.Error
    else -> DallimColors.TextSecondary
}

private fun statusIcon(status: String) = when (status) {
    SocialSessionCheckinStatus.CHECKED_IN -> DallimIcons.CircleCheck
    SocialSessionCheckinStatus.LATE -> DallimIcons.Clock
    SocialSessionCheckinStatus.NO_SHOW -> DallimIcons.UserX
    else -> DallimIcons.Circle
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SocialSessionCheckinScreenPreview() {
    DallimTheme {
        SocialSessionCheckinScreen(
            uiState = SocialSessionCheckinUiState.Success(
                myUserId = "usr_2",
                isHost = false,
                started = false,
                items = listOf(
                    SocialSessionReadyCheckItem(
                        userId = "usr_1", nickname = "달림이", avatarId = "avatar_02", isHost = true,
                        status = SocialSessionCheckinStatus.CHECKED_IN, checkedInAt = "2026-09-20T20:58:00Z",
                        distanceErrorM = 12.0, manualByHost = false,
                    ),
                    SocialSessionReadyCheckItem(
                        userId = "usr_2", nickname = "러너B", avatarId = "avatar_05", isHost = false,
                        status = SocialSessionCheckinStatus.WAITING, checkedInAt = null,
                        distanceErrorM = null, manualByHost = false,
                    ),
                ),
            ),
            onBackClick = {},
            onRetryClick = {},
            onCheckinClick = {},
            onStartClick = {},
            onManualConfirmClick = {},
            onFeedbackClick = {},
            onToastMessageShown = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SocialSessionCheckinScreenHostStartedPreview() {
    DallimTheme {
        SocialSessionCheckinScreen(
            uiState = SocialSessionCheckinUiState.Success(
                myUserId = "usr_1",
                isHost = true,
                started = true,
                items = listOf(
                    SocialSessionReadyCheckItem(
                        userId = "usr_1", nickname = "달림이", avatarId = "avatar_02", isHost = true,
                        status = SocialSessionCheckinStatus.CHECKED_IN, checkedInAt = "2026-09-20T20:58:00Z",
                        distanceErrorM = 12.0, manualByHost = false,
                    ),
                    SocialSessionReadyCheckItem(
                        userId = "usr_2", nickname = "러너B", avatarId = "avatar_05", isHost = false,
                        status = SocialSessionCheckinStatus.NO_SHOW, checkedInAt = null,
                        distanceErrorM = null, manualByHost = false,
                    ),
                ),
            ),
            onBackClick = {},
            onRetryClick = {},
            onCheckinClick = {},
            onStartClick = {},
            onManualConfirmClick = {},
            onFeedbackClick = {},
            onToastMessageShown = {},
        )
    }
}
