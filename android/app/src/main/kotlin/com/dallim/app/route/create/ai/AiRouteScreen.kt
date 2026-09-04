package com.dallim.app.route.create.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.dallim.ui.theme.DallimShapes
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
        onWaypointLongPress = viewModel::onWaypointLongPress,
        onWaypointClick = viewModel::onWaypointClick,
        onModeSelected = viewModel::onModeSelected,
        onDestinationLongPress = viewModel::onDestinationLongPress,
        onDestinationClearClick = viewModel::onDestinationClearClick,
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
    onWaypointLongPress: (GeoPoint) -> Unit,
    onWaypointClick: (GeoPoint) -> Unit,
    onModeSelected: (String) -> Unit,
    onDestinationLongPress: (GeoPoint) -> Unit,
    onDestinationClearClick: () -> Unit,
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
                    initialCenter = uiState.initialCenter,
                    requiredWaypoints = uiState.requiredWaypoints,
                    mode = uiState.mode,
                    destination = uiState.destination,
                    enabled = !uiState.isGenerating,
                    onDistanceChange = onDistanceChange,
                    onPaceSelected = onPaceSelected,
                    onWaypointLongPress = onWaypointLongPress,
                    onWaypointClick = onWaypointClick,
                    onModeSelected = onModeSelected,
                    onDestinationLongPress = onDestinationLongPress,
                    onDestinationClearClick = onDestinationClearClick,
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
    initialCenter: GeoPoint?,
    requiredWaypoints: List<GeoPoint>,
    mode: String,
    destination: GeoPoint?,
    enabled: Boolean,
    onDistanceChange: (Float) -> Unit,
    onPaceSelected: (ComfortablePace?) -> Unit,
    onWaypointLongPress: (GeoPoint) -> Unit,
    onWaypointClick: (GeoPoint) -> Unit,
    onModeSelected: (String) -> Unit,
    onDestinationLongPress: (GeoPoint) -> Unit,
    onDestinationClearClick: () -> Unit,
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

        Text(
            text = "코스 방식",
            style = DallimTypography.Title2,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Row(
            modifier = Modifier.padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            DallimFilterChip(label = "순환 코스", selected = mode == "LOOP", onClick = { onModeSelected("LOOP") })
            DallimFilterChip(
                label = "목적지 지정",
                selected = mode == "POINT_TO_POINT",
                onClick = { onModeSelected("POINT_TO_POINT") },
            )
        }

        if (BuildConfig.NAVER_MAP_CLIENT_ID_CONFIGURED) {
            if (mode == "POINT_TO_POINT") {
                DestinationSection(
                    initialCenter = initialCenter,
                    destination = destination,
                    onDestinationLongPress = onDestinationLongPress,
                    onDestinationClearClick = onDestinationClearClick,
                )
            } else {
                WaypointSection(
                    initialCenter = initialCenter,
                    requiredWaypoints = requiredWaypoints,
                    onWaypointLongPress = onWaypointLongPress,
                    onWaypointClick = onWaypointClick,
                )
            }
        }
    }
}

/**
 * "목적지 지정" 모드(docs/02-api-spec.md 11.4) — 지도 롱프레스로 도착지를 한 곳만 지정한다.
 * 다시 롱프레스하면 기존 지정을 덮어쓴다(여러 목적지는 개념적으로 말이 안 되므로 1개 고정).
 */
@Composable
private fun DestinationSection(
    initialCenter: GeoPoint?,
    destination: GeoPoint?,
    onDestinationLongPress: (GeoPoint) -> Unit,
    onDestinationClearClick: () -> Unit,
) {
    Text(
        text = "목적지",
        style = DallimTypography.Title2,
        color = DallimColors.TextPrimary,
        modifier = Modifier.padding(top = Spacing.lg),
    )
    Text(
        text = if (destination != null) "지도를 다시 길게 누르면 목적지가 바뀌어요" else "지도를 길게 눌러 목적지를 지정하세요",
        style = DallimTypography.Caption,
        color = DallimColors.TextSecondary,
        modifier = Modifier.padding(top = Spacing.xs),
    )
    Box(
        modifier = Modifier
            .padding(top = Spacing.sm)
            .fillMaxWidth()
            .height(180.dp)
            .clip(DallimShapes.CardCorner),
    ) {
        NaverRouteMapView(
            plannedRoute = emptyList(),
            actualRoute = emptyList(),
            initialCenter = initialCenter,
            waypoints = listOfNotNull(destination),
            onMapLongClick = onDestinationLongPress,
            onWaypointClick = { onDestinationClearClick() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * "꼭 지나갈 장소" 선택 — 지도 롱프레스로 최대 3곳 지정한다 (docs/02-api-spec.md 11.2,
 * docs/01-feature-spec.md 1.6). 꽉 찼을 때 롱프레스는 무시되고, 찍힌 마커를 탭하면 그 지점만
 * 삭제된다 — 별도 목록 UI 없이 지도 위 마커만으로 충분하다.
 *
 * NCP Client ID가 없는 로컬 빌드에서는 지도 자체가 없으므로(호출부에서 이미 분기) 이 옵션은
 * 그냥 노출하지 않는다 — 결과 미리보기처럼 Canvas 폴백을 만들 만큼 핵심 기능이 아니다.
 */
@Composable
private fun WaypointSection(
    initialCenter: GeoPoint?,
    requiredWaypoints: List<GeoPoint>,
    onWaypointLongPress: (GeoPoint) -> Unit,
    onWaypointClick: (GeoPoint) -> Unit,
) {
    Text(
        text = "꼭 지나갈 장소 (선택, 최대 3곳)",
        style = DallimTypography.Title2,
        color = DallimColors.TextPrimary,
        modifier = Modifier.padding(top = Spacing.lg),
    )
    Text(
        text = when {
            requiredWaypoints.isEmpty() -> "지도를 길게 눌러 지정하세요"
            requiredWaypoints.size < 3 -> "지도를 길게 눌러 추가하거나, 마커를 눌러 삭제하세요"
            else -> "최대 개수에 도달했어요 — 마커를 눌러 삭제할 수 있어요"
        },
        style = DallimTypography.Caption,
        color = DallimColors.TextSecondary,
        modifier = Modifier.padding(top = Spacing.xs),
    )
    Box(
        modifier = Modifier
            .padding(top = Spacing.sm)
            .fillMaxWidth()
            .height(180.dp)
            .clip(DallimShapes.CardCorner),
    ) {
        NaverRouteMapView(
            plannedRoute = emptyList(),
            actualRoute = emptyList(),
            initialCenter = initialCenter,
            waypoints = requiredWaypoints,
            onMapLongClick = onWaypointLongPress,
            onWaypointClick = onWaypointClick,
            modifier = Modifier.fillMaxSize(),
        )
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
                description = if (uiState.mode == "POINT_TO_POINT") {
                    "현재 위치에서 출발해 지정한 목적지까지 가는 코스를 만들어드려요."
                } else {
                    "현재 위치에서 출발해 다시 돌아오는 코스를 만들어드려요."
                },
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
            onWaypointLongPress = {},
            onWaypointClick = {},
            onModeSelected = {},
            onDestinationLongPress = {},
            onDestinationClearClick = {},
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
            onWaypointLongPress = {},
            onWaypointClick = {},
            onModeSelected = {},
            onDestinationLongPress = {},
            onDestinationClearClick = {},
            onGenerateClick = {},
            onSaveClick = {},
            onSnackbarShown = {},
        )
    }
}
