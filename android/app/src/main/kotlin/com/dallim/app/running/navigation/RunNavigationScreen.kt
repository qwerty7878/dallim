package com.dallim.app.running.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.BuildConfig
import com.dallim.app.location.RunPhase
import com.dallim.app.location.RunTrackingSnapshot
import com.dallim.app.running.RunFormat
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.components.DualRouteMapView
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.NaverRouteMapView
import com.dallim.ui.components.RunProgressRing
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-21 Sketch Navigation + S-22(일시정지/재개/종료) + S-23(코스 이탈) + S-24(완주판정 처리).
 * docs/04-ui-guide.md §9 예외 규칙: 다크 배경 고정, 최소 24sp(거리 28sp 이상), 지도/진행률/숫자3개/
 * 일시정지 버튼 외 다른 UI 요소를 넣지 않는다(이탈 배너·완주판정 오버레이는 spec이 명시한 S-23/
 * S-24 상태이므로 예외).
 */
@Composable
fun RunNavigationRoute(
    onFinished: (runId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RunNavigationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val voiceGuide = remember { RunVoiceGuide(context) }
    DisposableEffect(Unit) { onDispose { voiceGuide.shutdown() } }

    val trackingPhase = (uiState as? RunNavigationUiState.Tracking)?.snapshot?.phase
    BackHandler(enabled = trackingPhase == RunPhase.RUNNING || trackingPhase == RunPhase.PAUSED) {
        // 러닝 중에는 뒤로가기로 화면을 빠져나갈 수 없다 — 반드시 [종료]를 눌러야 한다.
    }

    var announcedStart by remember { mutableStateOf(false) }
    var lastAnnouncedKm by remember { mutableIntStateOf(0) }
    var wasOffRoute by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        val tracking = uiState as? RunNavigationUiState.Tracking ?: return@LaunchedEffect
        if (!announcedStart && tracking.snapshot.phase == RunPhase.RUNNING) {
            announcedStart = true
            voiceGuide.announceStart()
        }
        val km = (tracking.snapshot.distanceMeters / 1000).toInt()
        if (km > lastAnnouncedKm) {
            lastAnnouncedKm = km
            voiceGuide.announceKilometer(km)
        }
        if (tracking.snapshot.isOffRoute && !wasOffRoute) voiceGuide.announceOffRoute()
        wasOffRoute = tracking.snapshot.isOffRoute

        if (tracking.snapshot.phase == RunPhase.FINISHED) onFinished(viewModel.runId)
    }

    RunNavigationScreen(
        uiState = uiState,
        onPauseClick = {
            voiceGuide.announcePaused()
            viewModel.onPauseClick()
        },
        onResumeClick = viewModel::onResumeClick,
        onFinishClick = viewModel::onFinishClick,
        onRetryFinishClick = viewModel::onRetryFinishClick,
        modifier = modifier,
    )
}

@Composable
private fun RunNavigationScreen(
    uiState: RunNavigationUiState,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onFinishClick: () -> Unit,
    onRetryFinishClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.BackgroundDark),
    ) {
        when (uiState) {
            is RunNavigationUiState.Loading -> DarkCenteredMessage(text = "코스를 준비하고 있어요")
            is RunNavigationUiState.Error -> DarkCenteredMessage(text = uiState.message)
            is RunNavigationUiState.Tracking -> when (uiState.snapshot.phase) {
                RunPhase.FINISHING -> ProcessingOverlay(text = "GPS 기록을 업로드하고 있어요")
                RunPhase.FINISHED -> ProcessingOverlay(text = "결과를 준비하고 있어요")
                RunPhase.FINISH_FAILED -> FinishFailedOverlay(
                    message = uiState.snapshot.finishError ?: "완주 처리에 실패했어요",
                    onRetryClick = onRetryFinishClick,
                )
                RunPhase.IDLE, RunPhase.RUNNING, RunPhase.PAUSED -> TrackingContent(
                    plannedRoute = uiState.plannedRoute,
                    snapshot = uiState.snapshot,
                    onPauseClick = onPauseClick,
                    onResumeClick = onResumeClick,
                    onFinishClick = onFinishClick,
                )
            }
        }
    }
}

@Composable
private fun TrackingContent(
    plannedRoute: List<GeoPoint>,
    snapshot: RunTrackingSnapshot,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onFinishClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        Box(modifier = Modifier.weight(1f)) {
            // 네이버맵 NCP Client ID가 설정된 빌드에서만 실제 지도를 그린다 — 미설정 시(로컬 개발 등)
            // 크래시 없이 기존 Canvas 폴백으로 자동 전환된다 (docs/01-feature-spec.md §1.2).
            if (BuildConfig.NAVER_MAP_CLIENT_ID_CONFIGURED) {
                NaverRouteMapView(
                    plannedRoute = plannedRoute,
                    actualRoute = snapshot.actualPath,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                DualRouteMapView(
                    plannedRoute = plannedRoute,
                    actualRoute = snapshot.actualPath,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (snapshot.isOffRoute) {
                OffRouteBanner(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = Spacing.md),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RunProgressRing(progressPercent = snapshot.coveragePercent)
            Text(
                text = "그림 완성도",
                style = DallimTypography.Body,
                color = DallimColors.Surface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatColumn(label = "거리", value = "${RunFormat.distanceKm(snapshot.distanceMeters)}km")
            StatColumn(label = "시간", value = RunFormat.duration(snapshot.elapsedMillis / 1000))
            StatColumn(label = "페이스", value = RunFormat.pace(snapshot.currentPaceSecPerKm))
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            if (snapshot.phase == RunPhase.PAUSED) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                    CircularControlButton(icon = Icons.Filled.PlayArrow, contentDescription = "이어 달리기", onClick = onResumeClick)
                    DallimTextButton(text = "달리기 종료", onClick = onFinishClick)
                }
            } else {
                CircularControlButton(icon = Icons.Filled.Stop, contentDescription = "일시정지", onClick = onPauseClick, isPauseGlyph = true)
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = DallimTypography.NavLarge, color = DallimColors.Surface)
        Text(
            text = label,
            style = DallimTypography.Body,
            color = DallimColors.Surface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/** 러닝 중 [일시정지] 버튼 — 80dp 원형, 다른 UI와 확실히 구분 (docs/03-design-system.md §3.4). */
@Composable
private fun CircularControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isPauseGlyph: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(DallimShapes.PauseButtonSize)
            .clip(CircleShape)
            .background(DallimColors.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isPauseGlyph) {
            // 실제 "일시정지" 아이콘(두 막대)을 직접 그려 벡터 스톱 아이콘(정사각형)과 혼동을 줄인다.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(8.dp).height(28.dp).background(DallimColors.BackgroundDark))
                Box(Modifier.width(8.dp).height(28.dp).background(DallimColors.BackgroundDark))
            }
        } else {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = DallimColors.BackgroundDark, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun OffRouteBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(DallimShapes.ButtonCorner)
            .background(DallimColors.Warning)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(
            text = "코스에서 벗어났어요",
            style = DallimTypography.Body,
            color = DallimColors.BackgroundDark,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DarkCenteredMessage(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = DallimColors.Surface)
        Text(
            text = text,
            style = DallimTypography.Body,
            color = DallimColors.Surface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.md),
        )
    }
}

@Composable
private fun ProcessingOverlay(text: String) {
    DarkCenteredMessage(text = text)
}

@Composable
private fun FinishFailedOverlay(message: String, onRetryClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, style = DallimTypography.Body, color = DallimColors.Surface, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(Spacing.lg))
        DallimTextButton(text = "다시 시도", onClick = onRetryClick)
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun RunNavigationScreenRunningPreview() {
    RunNavigationScreen(
        uiState = RunNavigationUiState.Tracking(
            plannedRoute = listOf(GeoPoint(127.05, 37.25), GeoPoint(127.052, 37.253), GeoPoint(127.058, 37.256)),
            snapshot = RunTrackingSnapshot(
                phase = RunPhase.RUNNING,
                distanceMeters = 3200.0,
                elapsedMillis = 18 * 60 * 1000L + 42_000L,
                currentPaceSecPerKm = 352,
                actualPath = listOf(GeoPoint(127.05, 37.25), GeoPoint(127.051, 37.2515)),
                coveragePercent = 62,
                isOffRoute = false,
            ),
        ),
        onPauseClick = {},
        onResumeClick = {},
        onFinishClick = {},
        onRetryFinishClick = {},
    )
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun RunNavigationScreenPausedOffRoutePreview() {
    RunNavigationScreen(
        uiState = RunNavigationUiState.Tracking(
            plannedRoute = listOf(GeoPoint(127.05, 37.25), GeoPoint(127.052, 37.253)),
            snapshot = RunTrackingSnapshot(
                phase = RunPhase.PAUSED,
                distanceMeters = 1800.0,
                elapsedMillis = 10 * 60 * 1000L,
                currentPaceSecPerKm = 400,
                actualPath = listOf(GeoPoint(127.05, 37.25), GeoPoint(127.06, 37.26)),
                coveragePercent = 30,
                isOffRoute = true,
            ),
        ),
        onPauseClick = {},
        onResumeClick = {},
        onFinishClick = {},
        onRetryFinishClick = {},
    )
}
