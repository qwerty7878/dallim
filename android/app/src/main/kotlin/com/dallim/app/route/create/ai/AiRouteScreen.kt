package com.dallim.app.route.create.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.dallim.app.onboarding.profile.ComfortablePace
import com.dallim.app.running.RunFormat
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.route.RouteDiscoveryResponseBody
import com.dallim.ui.components.DallimEmptyState
import com.dallim.ui.components.DallimFilterChip
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimSecondaryButton
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.NaverRouteMapView
import com.dallim.ui.components.RouteThumbnailView
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-45 AI 자동 생성 — 현재 위치 + 목표 거리(+선택 페이스)로 순환 코스를 생성한다
 * (docs/02-api-spec.md 8.2, S-43에서 진입). docs/01-feature-spec.md에 없던 신설 화면.
 */
@Composable
fun AiRouteRoute(
    onBackClick: () -> Unit,
    viewModel: AiRouteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AiRouteScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onDistanceChange = viewModel::onDistanceChange,
        onPaceSelected = viewModel::onPaceSelected,
        onGenerateClick = viewModel::onGenerateClick,
        onSaveClick = viewModel::onSaveClick,
        onSnackbarShown = viewModel::onSnackbarShown,
    )
}

@Composable
private fun AiRouteScreen(
    uiState: AiRouteUiState,
    onBackClick: () -> Unit,
    onDistanceChange: (Float) -> Unit,
    onPaceSelected: (ComfortablePace?) -> Unit,
    onGenerateClick: () -> Unit,
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
                .statusBarsPadding()
                .navigationBarsPadding(),
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
                Text(text = "AI로 자동 생성", style = DallimTypography.Title1, color = DallimColors.TextPrimary)
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                InputSection(
                    targetDistanceKm = uiState.targetDistanceKm,
                    pace = uiState.pace,
                    enabled = !uiState.isGenerating,
                    onDistanceChange = onDistanceChange,
                    onPaceSelected = onPaceSelected,
                )

                ResultSection(uiState = uiState)
            }

            BottomPanel(
                uiState = uiState,
                onGenerateClick = onGenerateClick,
                onSaveClick = onSaveClick,
            )
        }
    }
}

@Composable
private fun InputSection(
    targetDistanceKm: Float,
    pace: ComfortablePace?,
    enabled: Boolean,
    onDistanceChange: (Float) -> Unit,
    onPaceSelected: (ComfortablePace?) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md)) {
        Text(text = "목표 거리", style = DallimTypography.Title2, color = DallimColors.TextPrimary)
        Text(
            text = "%.1fkm".format(targetDistanceKm),
            style = DallimTypography.Title1,
            color = DallimColors.Primary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Slider(
            value = targetDistanceKm,
            onValueChange = onDistanceChange,
            valueRange = 1f..15f,
            steps = 27, // 0.5km 단위 (1.0~15.0)
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = DallimColors.Primary,
                activeTrackColor = DallimColors.Primary,
                inactiveTrackColor = DallimColors.Border,
            ),
        )

        Text(
            text = "페이스 (선택)",
            style = DallimTypography.Title2,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        LazyRow(
            modifier = Modifier.padding(top = Spacing.sm),
            contentPadding = PaddingValues(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(ComfortablePace.entries.toList()) { option ->
                DallimFilterChip(
                    label = option.label,
                    selected = pace == option,
                    onClick = { onPaceSelected(option) },
                )
            }
        }
    }
}

@Composable
private fun ResultSection(uiState: AiRouteUiState) {
    val result = uiState.result

    Column(modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md)) {
        if (uiState.errorMessage != null) {
            Text(text = uiState.errorMessage, style = DallimTypography.Caption, color = DallimColors.Error)
        }

        when {
            uiState.isGenerating -> Text(
                text = "코스를 만들고 있어요. 몇 초 정도 걸릴 수 있어요.",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            result != null -> {
                Column(modifier = Modifier.padding(top = Spacing.sm)) {
                    if (BuildConfig.NAVER_MAP_CLIENT_ID_CONFIGURED) {
                        NaverRouteMapView(
                            plannedRoute = result.geoJson.toGeoPoints(),
                            actualRoute = emptyList(),
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                    } else {
                        RouteThumbnailView(
                            coordinates = result.geoJson.toGeoPoints(),
                            useGradient = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        text = "${RunFormat.km(result.distanceKm)}km · 약 ${result.estimatedMinutes}분",
                        style = DallimTypography.Body,
                        color = DallimColors.TextPrimary,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }
            else -> DallimEmptyState(
                title = "아래 버튼으로 코스를 만들어보세요",
                description = "현재 위치에서 출발해 다시 돌아오는 코스를 만들어드려요.",
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun BottomPanel(
    uiState: AiRouteUiState,
    onGenerateClick: () -> Unit,
    onSaveClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DallimColors.Surface)
            .padding(Spacing.ScreenHorizontal),
    ) {
        if (uiState.result != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DallimSecondaryButton(
                    text = "다시 생성",
                    onClick = onGenerateClick,
                    enabled = !uiState.isGenerating,
                    modifier = Modifier.weight(1f),
                )
                DallimPrimaryButton(text = "저장", onClick = onSaveClick, modifier = Modifier.weight(1f))
            }
        } else {
            DallimPrimaryButton(
                text = if (uiState.isGenerating) "생성 중이에요..." else "코스 생성",
                onClick = onGenerateClick,
                enabled = !uiState.isGenerating,
            )
        }
    }
}

private fun GeoJsonLineString.toGeoPoints(): List<GeoPoint> =
    toLngLatPairs().map { (lng, lat) -> GeoPoint(lng = lng, lat = lat) }

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun AiRouteScreenPreview() {
    DallimTheme {
        AiRouteScreen(
            uiState = AiRouteUiState(targetDistanceKm = 5f, pace = ComfortablePace.PACE_6_7),
            onBackClick = {},
            onDistanceChange = {},
            onPaceSelected = {},
            onGenerateClick = {},
            onSaveClick = {},
            onSnackbarShown = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900, name = "Result")
@Composable
private fun AiRouteScreenResultPreview() {
    DallimTheme {
        AiRouteScreen(
            uiState = AiRouteUiState(
                targetDistanceKm = 5f,
                result = RouteDiscoveryResponseBody(
                    geoJson = GeoJsonLineString(
                        coordinates = listOf(
                            listOf(127.05, 37.25),
                            listOf(127.052, 37.253),
                            listOf(127.055, 37.251),
                            listOf(127.05, 37.25),
                        ),
                    ),
                    distanceKm = 4.87,
                    estimatedMinutes = 34,
                ),
            ),
            onBackClick = {},
            onDistanceChange = {},
            onPaceSelected = {},
            onGenerateClick = {},
            onSaveClick = {},
            onSnackbarShown = {},
        )
    }
}
