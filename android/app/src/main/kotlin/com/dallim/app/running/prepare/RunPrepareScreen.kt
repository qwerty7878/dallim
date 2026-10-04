package com.dallim.app.running.prepare

import com.dallim.ui.icons.DallimIcons
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.running.RunFormat
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.route.RouteDetailResponseBody
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-20 러닝 준비 (docs/01-feature-spec.md §1.3) — GPS 신호/배터리 최적화/백그라운드 위치 권한
 * 체크리스트 + 3초 카운트다운. **백그라운드 위치 권한은 여기서 최초로 요청한다** (S-05가 아님).
 */
@Composable
fun RunPrepareRoute(
    onStarted: (runId: String, routeId: String?) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RunPrepareViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState) {
        val started = uiState as? RunPrepareUiState.Started ?: return@LaunchedEffect
        onStarted(started.runId, started.routeId)
    }

    // Android 10(API 29)부터 별도로 존재하는 백그라운드 위치 권한. 그 이전 버전은 포그라운드
    // 권한에 백그라운드 접근이 포함돼 있어 별도 요청이 필요 없다.
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onBackgroundLocationPermissionChanged(granted) }

    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        requestBackgroundLocationIfNeeded(context, backgroundLocationLauncher, viewModel)
    }

    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onBatteryOptimizationStatusChanged(isIgnoringBatteryOptimizations(context))
    }

    LaunchedEffect(Unit) {
        viewModel.onBatteryOptimizationStatusChanged(isIgnoringBatteryOptimizations(context))
        viewModel.onBackgroundLocationPermissionChanged(hasBackgroundLocationPermission(context))
    }

    RunPrepareScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetryClick = viewModel::load,
        onRefreshGpsClick = viewModel::checkGpsSignal,
        onRequestBackgroundLocationClick = {
            if (hasForegroundLocationPermission(context)) {
                requestBackgroundLocationIfNeeded(context, backgroundLocationLauncher, viewModel)
            } else {
                foregroundLocationLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            }
        },
        onRequestBatteryOptimizationClick = {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            runCatching { batteryOptimizationLauncher.launch(intent) }
        },
        // FOREGROUND_SERVICE_TYPE_LOCATION은 포그라운드 위치 권한이 이미 허용돼 있을 것을
        // 플랫폼이 강제한다 — 없는 채로 진행하면 LocationTrackingService가 SecurityException으로
        // 크래시한다. 그래서 이 권한만은 배터리 최적화/백그라운드 권한과 달리 "허용/거부 무관
        // 진행"을 적용하지 않고, 없으면 달리기를 시작하는 대신 권한 요청부터 띄운다.
        onStartRunClick = {
            if (hasForegroundLocationPermission(context)) {
                viewModel.onStartRunClick()
            } else {
                foregroundLocationLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            }
        },
        onCancelCountdown = viewModel::onCancelCountdown,
        modifier = modifier,
    )
}

private fun requestBackgroundLocationIfNeeded(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<String>,
    viewModel: RunPrepareViewModel,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        viewModel.onBackgroundLocationPermissionChanged(true)
        return
    }
    if (hasBackgroundLocationPermission(context)) {
        viewModel.onBackgroundLocationPermissionChanged(true)
    } else {
        launcher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
}

private fun hasForegroundLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

private fun hasBackgroundLocationPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        true
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
private fun RunPrepareScreen(
    uiState: RunPrepareUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onRefreshGpsClick: () -> Unit,
    onRequestBackgroundLocationClick: () -> Unit,
    onRequestBatteryOptimizationClick: () -> Unit,
    onStartRunClick: () -> Unit,
    onCancelCountdown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = DallimIcons.ArrowLeft,
                    contentDescription = "뒤로가기",
                    tint = DallimColors.TextPrimary,
                )
            }
        }

        when (uiState) {
            is RunPrepareUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
            is RunPrepareUiState.Error -> DallimErrorState(
                title = "코스를 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            is RunPrepareUiState.Started -> DallimLoadingState(modifier = Modifier.weight(1f))
            is RunPrepareUiState.Ready -> ReadyContent(
                uiState = uiState,
                onRefreshGpsClick = onRefreshGpsClick,
                onRequestBackgroundLocationClick = onRequestBackgroundLocationClick,
                onRequestBatteryOptimizationClick = onRequestBatteryOptimizationClick,
                onStartRunClick = onStartRunClick,
                onCancelCountdown = onCancelCountdown,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReadyContent(
    uiState: RunPrepareUiState.Ready,
    onRefreshGpsClick: () -> Unit,
    onRequestBackgroundLocationClick: () -> Unit,
    onRequestBatteryOptimizationClick: () -> Unit,
    onStartRunClick: () -> Unit,
    onCancelCountdown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.countdownSecondsRemaining != null) {
        CountdownOverlay(
            secondsRemaining = uiState.countdownSecondsRemaining,
            onCancel = onCancelCountdown,
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.padding(horizontal = Spacing.ScreenHorizontal)) {
        Text(
            text = "달릴 준비를 확인할게요",
            style = DallimTypography.Title1,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        val route = uiState.route
        if (route != null) {
            Row(modifier = Modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(text = route.emoji, fontSize = 20.sp)
                Text(
                    text = "${route.name} · ${RunFormat.km(route.distanceKm)}km",
                    style = DallimTypography.Body,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        } else {
            // 자유 러닝(2026-09-26, 사용자 요청) — 목표 코스 없이 바로 시작.
            Text(
                text = "자유 러닝 · 달린 만큼이 그림이 돼요",
                style = DallimTypography.Body,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        Column(modifier = Modifier.padding(top = Spacing.xl)) {
            ChecklistRow(
                icon = DallimIcons.LocateFixed,
                title = "GPS 신호 강도",
                statusText = uiState.gpsSignal.toLabel(uiState.gpsAccuracyM),
                isSatisfied = uiState.gpsSignal == GpsSignalStrength.STRONG,
                actionText = if (uiState.gpsSignal == GpsSignalStrength.CHECKING) null else "다시 확인",
                onActionClick = onRefreshGpsClick,
            )
            ChecklistRow(
                icon = DallimIcons.BatteryWarning,
                title = "배터리 최적화 제외",
                statusText = if (uiState.isBatteryOptimizationIgnored) "설정 완료" else "권장 — 러닝 중 GPS가 꺼질 수 있어요",
                isSatisfied = uiState.isBatteryOptimizationIgnored,
                actionText = if (uiState.isBatteryOptimizationIgnored) null else "설정하기",
                onActionClick = onRequestBatteryOptimizationClick,
                modifier = Modifier.padding(top = Spacing.md),
            )
            ChecklistRow(
                icon = DallimIcons.LocateFixed,
                title = "백그라운드 위치 권한",
                statusText = if (uiState.hasBackgroundLocationPermission) "허용됨" else "화면이 꺼져도 기록하려면 필요해요",
                isSatisfied = uiState.hasBackgroundLocationPermission,
                actionText = if (uiState.hasBackgroundLocationPermission) null else "허용하기",
                onActionClick = onRequestBackgroundLocationClick,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }

        if (uiState.startError != null) {
            Text(
                text = uiState.startError,
                style = DallimTypography.Caption,
                color = DallimColors.Error,
                modifier = Modifier.padding(top = Spacing.lg),
            )
        }

        Box(modifier = Modifier.weight(1f))

        DallimPrimaryButton(
            text = "달리기 시작",
            onClick = onStartRunClick,
            enabled = !uiState.isStarting,
            modifier = Modifier.padding(bottom = Spacing.xl),
        )
    }
}

@Composable
private fun ChecklistRow(
    icon: ImageVector,
    title: String,
    statusText: String,
    isSatisfied: Boolean,
    actionText: String?,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = DallimColors.TextSecondary, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
            Text(text = title, style = DallimTypography.Body, color = DallimColors.TextPrimary)
            Text(text = statusText, style = DallimTypography.Caption, color = DallimColors.TextSecondary)
        }
        if (actionText != null) {
            DallimTextButton(text = actionText, onClick = onActionClick)
        } else {
            Icon(
                imageVector = if (isSatisfied) DallimIcons.CircleCheck else DallimIcons.Circle,
                contentDescription = null,
                tint = if (isSatisfied) DallimColors.Success else DallimColors.TextSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** 3초 카운트다운 — 다음 화면(S-21)이 다크 고정이므로 여기도 미리 다크 배경으로 전환한다. */
@Composable
private fun CountdownOverlay(secondsRemaining: Int, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().background(DallimColors.BackgroundDark),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "$secondsRemaining",
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
            color = DallimColors.TextPrimary,
        )
        Text(
            text = "곧 달리기가 시작돼요",
            style = DallimTypography.Body,
            color = DallimColors.TextPrimary.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.md),
        )
        DallimTextButton(text = "취소", onClick = onCancel, modifier = Modifier.padding(top = Spacing.xl))
    }
}

private fun GpsSignalStrength.toLabel(accuracyM: Float?): String = when (this) {
    GpsSignalStrength.CHECKING -> "확인 중..."
    GpsSignalStrength.STRONG -> "강함 (오차 약 ${accuracyM?.toInt() ?: 0}m)"
    GpsSignalStrength.MODERATE -> "보통 (오차 약 ${accuracyM?.toInt() ?: 0}m)"
    GpsSignalStrength.WEAK -> "약함 — 트인 공간으로 이동해보세요"
    GpsSignalStrength.UNAVAILABLE -> "확인할 수 없어요 — 위치 권한을 확인해주세요"
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun RunPrepareScreenPreview() {
    DallimTheme {
        RunPrepareScreen(
            uiState = RunPrepareUiState.Ready(
                route = RouteDetailResponseBody(
                    routeId = "rt_001",
                    name = "고래",
                    emoji = "🐳",
                    geoJson = GeoJsonLineString(),
                    distanceKm = 5.1,
                    estimatedMinutes = 36,
                    difficulty = "EASY",
                    status = "POPULAR",
                    finisherCount = 148,
                    trafficLightCount = 4,
                    elevationGainM = 32,
                    repeatSegmentPercent = 5,
                    runability = 0.87,
                    isSaved = false,
                ),
                gpsSignal = GpsSignalStrength.STRONG,
                gpsAccuracyM = 8f,
                isBatteryOptimizationIgnored = false,
                hasBackgroundLocationPermission = false,
            ),
            onBackClick = {},
            onRetryClick = {},
            onRefreshGpsClick = {},
            onRequestBackgroundLocationClick = {},
            onRequestBatteryOptimizationClick = {},
            onStartRunClick = {},
            onCancelCountdown = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun RunPrepareScreenFreeformPreview() {
    DallimTheme {
        RunPrepareScreen(
            uiState = RunPrepareUiState.Ready(
                route = null,
                gpsSignal = GpsSignalStrength.STRONG,
                gpsAccuracyM = 8f,
                isBatteryOptimizationIgnored = true,
                hasBackgroundLocationPermission = true,
            ),
            onBackClick = {},
            onRetryClick = {},
            onRefreshGpsClick = {},
            onRequestBackgroundLocationClick = {},
            onRequestBatteryOptimizationClick = {},
            onStartRunClick = {},
            onCancelCountdown = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun RunPrepareCountdownPreview() {
    DallimTheme {
        CountdownOverlay(secondsRemaining = 3, onCancel = {})
    }
}
