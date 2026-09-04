package com.dallim.app.route.create.draw

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.BuildConfig
import com.dallim.app.running.RunFormat
import com.dallim.network.route.RouteDrawConvertResponseBody
import com.dallim.ui.components.DallimCheckboxRow
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimSecondaryButton
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.NaverDrawableMapView
import com.dallim.ui.components.NaverRouteMapView
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-44 직접 그리기 — 지도 위에 손가락으로 그린 궤적을 실도로 코스로 변환한다
 * (docs/02-api-spec.md 8.1, S-43에서 진입). docs/01-feature-spec.md에 없던 신설 화면.
 *
 * NCP Client ID가 설정되지 않은 로컬 빌드에서는 그리기 자체가 성립하지 않으므로(네이버맵
 * SDK가 필요) [RouteDetailScreen]처럼 Canvas 폴백을 두지 않고 에러 상태로 안내한다.
 */
@Composable
fun DrawRouteRoute(
    onBackClick: () -> Unit,
    viewModel: DrawRouteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DrawRouteScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onPointDrawn = viewModel::onPointDrawn,
        onClearClick = viewModel::onClearClick,
        onCloseLoopToggle = viewModel::onCloseLoopToggle,
        onConvertClick = viewModel::onConvertClick,
        onRedrawClick = viewModel::onRedrawClick,
        onSaveClick = viewModel::onSaveClick,
        onSnackbarShown = viewModel::onSnackbarShown,
    )
}

@Composable
private fun DrawRouteScreen(
    uiState: DrawRouteUiState,
    onBackClick: () -> Unit,
    onPointDrawn: (GeoPoint) -> Unit,
    onClearClick: () -> Unit,
    onCloseLoopToggle: (Boolean) -> Unit,
    onConvertClick: () -> Unit,
    onRedrawClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSnackbarShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onSnackbarShown()
    }

    Scaffold(
        containerColor = DallimColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DallimColors.Background)
                .padding(innerPadding)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로가기",
                        tint = DallimColors.TextPrimary,
                    )
                }
                Text(text = "직접 그리기", style = DallimTypography.Title1, color = DallimColors.TextPrimary)
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !BuildConfig.NAVER_MAP_CLIENT_ID_CONFIGURED -> DallimErrorState(
                        title = "지도를 사용할 수 없어요",
                        description = "네이버맵 설정이 필요해요.",
                    )
                    uiState.convertResult != null -> NaverRouteMapView(
                        plannedRoute = uiState.drawnPoints,
                        actualRoute = uiState.convertResult.geoJson.toGeoPoints(),
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> NaverDrawableMapView(
                        drawnPoints = uiState.drawnPoints,
                        onPointDrawn = onPointDrawn,
                        initialCenter = uiState.initialCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (uiState.isConverting) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(DallimColors.BackgroundDark.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = DallimColors.Surface)
                            Text(
                                text = "실제 도로에 맞춰 변환하는 중이에요",
                                style = DallimTypography.Body,
                                color = DallimColors.Surface,
                                modifier = Modifier.padding(top = Spacing.md),
                            )
                        }
                    }
                }
            }

            BottomPanel(
                uiState = uiState,
                onClearClick = onClearClick,
                onCloseLoopToggle = onCloseLoopToggle,
                onConvertClick = onConvertClick,
                onRedrawClick = onRedrawClick,
                onSaveClick = onSaveClick,
            )
        }
    }
}

@Composable
private fun BottomPanel(
    uiState: DrawRouteUiState,
    onClearClick: () -> Unit,
    onCloseLoopToggle: (Boolean) -> Unit,
    onConvertClick: () -> Unit,
    onRedrawClick: () -> Unit,
    onSaveClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DallimColors.Surface)
            .navigationBarsPadding()
            .padding(Spacing.ScreenHorizontal),
    ) {
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                style = DallimTypography.Caption,
                color = DallimColors.Error,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }

        val result = uiState.convertResult
        if (result != null) {
            Text(
                text = "${RunFormat.km(result.distanceKm)}km 코스로 변환됐어요",
                style = DallimTypography.Body,
                color = DallimColors.TextPrimary,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DallimSecondaryButton(text = "다시 그리기", onClick = onRedrawClick, modifier = Modifier.weight(1f))
                DallimPrimaryButton(text = "저장", onClick = onSaveClick, modifier = Modifier.weight(1f))
            }
        } else {
            if (uiState.drawnPoints.isNotEmpty() && uiState.drawnPoints.size < 10) {
                Text(
                    text = "조금 더 길게 그려주세요 (${uiState.drawnPoints.size}/10)",
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            DallimCheckboxRow(
                checked = uiState.closeLoop,
                onCheckedChange = onCloseLoopToggle,
                label = "출발점으로 돌아오기",
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DallimSecondaryButton(
                    text = "지우기",
                    onClick = onClearClick,
                    enabled = uiState.drawnPoints.isNotEmpty() && !uiState.isConverting,
                    modifier = Modifier.weight(1f),
                )
                DallimPrimaryButton(
                    text = "완료",
                    onClick = onConvertClick,
                    enabled = uiState.canConvert,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun com.dallim.network.common.GeoJsonLineString.toGeoPoints(): List<GeoPoint> =
    toLngLatPairs().map { (lng, lat) -> GeoPoint(lng = lng, lat = lat) }

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun DrawRouteScreenPreview() {
    DallimTheme {
        DrawRouteScreen(
            uiState = DrawRouteUiState(
                drawnPoints = listOf(GeoPoint(127.05, 37.25), GeoPoint(127.052, 37.253)),
            ),
            onBackClick = {},
            onPointDrawn = {},
            onClearClick = {},
            onCloseLoopToggle = {},
            onConvertClick = {},
            onRedrawClick = {},
            onSaveClick = {},
            onSnackbarShown = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900, name = "Converted")
@Composable
private fun DrawRouteScreenConvertedPreview() {
    DallimTheme {
        DrawRouteScreen(
            uiState = DrawRouteUiState(
                drawnPoints = listOf(GeoPoint(127.05, 37.25), GeoPoint(127.052, 37.253)),
                convertResult = RouteDrawConvertResponseBody(
                    geoJson = com.dallim.network.common.GeoJsonLineString(
                        coordinates = listOf(listOf(127.05, 37.25), listOf(127.0525, 37.2535)),
                    ),
                    distanceKm = 3.42,
                ),
            ),
            onBackClick = {},
            onPointDrawn = {},
            onClearClick = {},
            onCloseLoopToggle = {},
            onConvertClick = {},
            onRedrawClick = {},
            onSaveClick = {},
            onSnackbarShown = {},
        )
    }
}
