package com.dallim.app.route.create.ai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.BuildConfig
import com.dallim.app.onboarding.profile.ComfortablePace
import com.dallim.app.running.RunFormat
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.route.PlaceSearchItem
import com.dallim.network.route.RouteDiscoveryResponseBody
import com.dallim.ui.components.DallimEmptyState
import com.dallim.ui.components.DallimFilterChip
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimSecondaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.components.DallimTextField
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.NaverRouteMapView
import com.dallim.ui.components.RouteThumbnailView
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    val activity = LocalContext.current.findActivity()

    AiRouteScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onDistanceChange = viewModel::onDistanceChange,
        onPaceSelected = viewModel::onPaceSelected,
        onWaypointLongPress = viewModel::onWaypointLongPress,
        onWaypointClick = viewModel::onWaypointClick,
        onPlaceSearchQueryChange = viewModel::onPlaceSearchQueryChange,
        onPlaceSearchResultClick = viewModel::onPlaceSearchResultClick,
        onModeSelected = viewModel::onModeSelected,
        onDestinationLongPress = viewModel::onDestinationLongPress,
        onDestinationClearClick = viewModel::onDestinationClearClick,
        onShapeTypeSelected = viewModel::onShapeTypeSelected,
        onShapeSizeSelected = viewModel::onShapeSizeSelected,
        onGenerateClick = viewModel::onGenerateClick,
        onSaveClick = viewModel::onSaveClick,
        onSnackbarShown = viewModel::onSnackbarShown,
        onQuotaExceededModalDismiss = viewModel::onQuotaExceededModalDismiss,
        onWatchAdClick = { viewModel.onWatchAdClick(activity) },
    )
}

/** 리워드 광고(`RewardedAd.show`)는 SDK 제약상 Activity Context가 있어야 한다. */
private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("AiRouteScreen은 Activity Context 내에서만 사용할 수 있어요.")
}

@Composable
private fun AiRouteScreen(
    uiState: AiRouteUiState,
    onBackClick: () -> Unit,
    onDistanceChange: (Float) -> Unit,
    onPaceSelected: (ComfortablePace?) -> Unit,
    onWaypointLongPress: (GeoPoint) -> Unit,
    onWaypointClick: (GeoPoint) -> Unit,
    onPlaceSearchQueryChange: (String) -> Unit,
    onPlaceSearchResultClick: (PlaceSearchItem) -> Unit,
    onModeSelected: (String) -> Unit,
    onDestinationLongPress: (GeoPoint) -> Unit,
    onDestinationClearClick: () -> Unit,
    onShapeTypeSelected: (String) -> Unit,
    onShapeSizeSelected: (String) -> Unit,
    onGenerateClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSnackbarShown: () -> Unit,
    onQuotaExceededModalDismiss: () -> Unit,
    onWatchAdClick: () -> Unit,
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

            // v1.3 문서 257행 "남은 무료 탐색 횟수 표시" — 아직 못 불러왔으면(null) 배너 자체를 숨긴다.
            uiState.quotaRemainingToday?.let { remaining ->
                Text(
                    text = "오늘 남은 무료 탐색 ${remaining}회",
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.xs),
                )
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                InputSection(
                    targetDistanceKm = uiState.targetDistanceKm,
                    pace = uiState.pace,
                    initialCenter = uiState.initialCenter,
                    requiredWaypoints = uiState.requiredWaypoints,
                    placeSearchQuery = uiState.placeSearchQuery,
                    placeSearchResults = uiState.placeSearchResults,
                    isSearchingPlaces = uiState.isSearchingPlaces,
                    mode = uiState.mode,
                    destination = uiState.destination,
                    shapeType = uiState.shapeType,
                    shapeSize = uiState.shapeSize,
                    enabled = !uiState.isGenerating,
                    onDistanceChange = onDistanceChange,
                    onPaceSelected = onPaceSelected,
                    onWaypointLongPress = onWaypointLongPress,
                    onWaypointClick = onWaypointClick,
                    onPlaceSearchQueryChange = onPlaceSearchQueryChange,
                    onPlaceSearchResultClick = onPlaceSearchResultClick,
                    onModeSelected = onModeSelected,
                    onDestinationLongPress = onDestinationLongPress,
                    onDestinationClearClick = onDestinationClearClick,
                    onShapeTypeSelected = onShapeTypeSelected,
                    onShapeSizeSelected = onShapeSizeSelected,
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

    if (uiState.showQuotaExceededModal) {
        QuotaExceededDialog(
            isWatchingAd = uiState.isWatchingAd,
            onWatchAdClick = onWatchAdClick,
            onDismiss = onQuotaExceededModalDismiss,
        )
    }
}

/**
 * v1.3 문서 259행 "일 무료 횟수 소진" 모달 — `[광고 보고 1회 더]` 버튼과 닫기만 있다. RUN+(구독)
 * 옵션은 아직 구현된 적 없는 기능이라 SPEC 임의 확장 금지 원칙상 만들지 않는다
 * (docs/02-api-spec.md 8.4).
 */
@Composable
private fun QuotaExceededDialog(
    isWatchingAd: Boolean,
    onWatchAdClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!isWatchingAd) onDismiss() }) {
        Surface(shape = DallimShapes.CardCorner, color = DallimColors.Surface) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(
                    text = "오늘의 무료 탐색을 모두 사용했어요",
                    style = DallimTypography.Title2,
                    color = DallimColors.TextPrimary,
                )
                Text(
                    text = "광고를 보면 오늘 코스 생성 횟수를 2회 더 받을 수 있어요.",
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End),
                ) {
                    DallimTextButton(text = "닫기", onClick = onDismiss, enabled = !isWatchingAd)
                    DallimTextButton(
                        text = if (isWatchingAd) "불러오는 중..." else "광고 보고 1회 더",
                        onClick = onWatchAdClick,
                        enabled = !isWatchingAd,
                    )
                }
            }
        }
    }
}

@Composable
private fun InputSection(
    targetDistanceKm: Float,
    pace: ComfortablePace?,
    initialCenter: GeoPoint?,
    requiredWaypoints: List<GeoPoint>,
    placeSearchQuery: String,
    placeSearchResults: List<PlaceSearchItem>,
    isSearchingPlaces: Boolean,
    mode: String,
    destination: GeoPoint?,
    shapeType: String?,
    shapeSize: String,
    enabled: Boolean,
    onDistanceChange: (Float) -> Unit,
    onPaceSelected: (ComfortablePace?) -> Unit,
    onWaypointLongPress: (GeoPoint) -> Unit,
    onWaypointClick: (GeoPoint) -> Unit,
    onPlaceSearchQueryChange: (String) -> Unit,
    onPlaceSearchResultClick: (PlaceSearchItem) -> Unit,
    onModeSelected: (String) -> Unit,
    onDestinationLongPress: (GeoPoint) -> Unit,
    onDestinationClearClick: () -> Unit,
    onShapeTypeSelected: (String) -> Unit,
    onShapeSizeSelected: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md)) {
        // SHAPE 모드에서는 서버가 targetDistanceKm을 무시하고 size(S/M/L)로 스케일을 고정하므로
        // 슬라이더 자체를 숨긴다 — 무시되는 입력을 받게 두면 혼란만 준다(docs/02-api-spec.md 13.5).
        if (mode != "SHAPE") {
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
        }

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
            DallimFilterChip(label = "모양 선택", selected = mode == "SHAPE", onClick = { onModeSelected("SHAPE") })
        }

        if (mode == "SHAPE") {
            // SHAPE는 지도를 쓰지 않는 입력이라 NCP Client ID 유무와 무관하게 항상 노출한다
            // (docs/02-api-spec.md 13장, requiredWaypoints/destination과 조합 불가 — 13.4).
            ShapeSection(
                shapeType = shapeType,
                shapeSize = shapeSize,
                onShapeTypeSelected = onShapeTypeSelected,
                onShapeSizeSelected = onShapeSizeSelected,
            )
        } else if (BuildConfig.NAVER_MAP_CLIENT_ID_CONFIGURED) {
            if (mode == "POINT_TO_POINT") {
                DestinationSection(
                    initialCenter = initialCenter,
                    destination = destination,
                    onDestinationLongPress = onDestinationLongPress,
                    onDestinationClearClick = onDestinationClearClick,
                )
            }
            // 목적지 지정 모드에서도 꼭 지나갈 장소를 함께 켤 수 있다 (docs/01-feature-spec.md 1.6,
            // docs/02-api-spec.md 11.6) — 두 지도 카드는 서로 독립된 상태/핸들러를 갖는다.
            WaypointSection(
                initialCenter = initialCenter,
                requiredWaypoints = requiredWaypoints,
                placeSearchQuery = placeSearchQuery,
                placeSearchResults = placeSearchResults,
                isSearchingPlaces = isSearchingPlaces,
                onWaypointLongPress = onWaypointLongPress,
                onWaypointClick = onWaypointClick,
                onPlaceSearchQueryChange = onPlaceSearchQueryChange,
                onPlaceSearchResultClick = onPlaceSearchResultClick,
            )
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
 * "꼭 지나갈 장소" 선택 — 지도 롱프레스로 최대 3곳 지정한다 (docs/02-api-spec.md 11.2/11.6,
 * docs/01-feature-spec.md 1.6). 순환 코스뿐 아니라 목적지 지정 모드에서도 함께 노출되어(11.6),
 * [DestinationSection]과는 별개의 지도 카드로 독립된 상태를 갖는다. 꽉 찼을 때 롱프레스는
 * 무시되고, 찍힌 마커를 탭하면 그 지점만 삭제된다 — 별도 목록 UI 없이 지도 위 마커만으로 충분하다.
 *
 * NCP Client ID가 없는 로컬 빌드에서는 지도 자체가 없으므로(호출부에서 이미 분기) 이 옵션은
 * 그냥 노출하지 않는다 — 결과 미리보기처럼 Canvas 폴백을 만들 만큼 핵심 기능이 아니다.
 */
@Composable
private fun WaypointSection(
    initialCenter: GeoPoint?,
    requiredWaypoints: List<GeoPoint>,
    placeSearchQuery: String,
    placeSearchResults: List<PlaceSearchItem>,
    isSearchingPlaces: Boolean,
    onWaypointLongPress: (GeoPoint) -> Unit,
    onWaypointClick: (GeoPoint) -> Unit,
    onPlaceSearchQueryChange: (String) -> Unit,
    onPlaceSearchResultClick: (PlaceSearchItem) -> Unit,
) {
    Text(
        text = "꼭 지나갈 장소 (선택, 최대 3곳)",
        style = DallimTypography.Title2,
        color = DallimColors.TextPrimary,
        modifier = Modifier.padding(top = Spacing.lg),
    )
    Text(
        text = when {
            requiredWaypoints.isEmpty() -> "지도를 길게 누르거나, 장소를 검색해서 지정하세요"
            requiredWaypoints.size < 3 -> "지도를 길게 눌러 추가하거나, 마커를 눌러 삭제하세요"
            else -> "최대 개수에 도달했어요 — 마커를 눌러 삭제할 수 있어요"
        },
        style = DallimTypography.Caption,
        color = DallimColors.TextSecondary,
        modifier = Modifier.padding(top = Spacing.xs),
    )

    // 지도 롱프레스를 대체하는 게 아니라 추가되는 입력 수단 — 선택한 결과는 그대로
    // onWaypointLongPress로 흘러가 롱프레스와 동일한 3곳 상한/마커 렌더링을 그대로 탄다.
    PlaceSearchField(
        query = placeSearchQuery,
        results = placeSearchResults,
        isSearching = isSearchingPlaces,
        onQueryChange = onPlaceSearchQueryChange,
        onResultClick = onPlaceSearchResultClick,
        modifier = Modifier.padding(top = Spacing.sm),
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

/**
 * "꼭 지나갈 장소" 검색창 (docs/02-api-spec.md 12장). debounce는 [AiRouteViewModel]에서
 * 클라이언트 책임으로 처리하므로 여기서는 입력값을 그대로 흘려보내기만 한다. 검색 결과가 항상
 * 빈 배열일 수 있으므로(백엔드 키 미설정 시에도) "검색했지만 결과 없음"을 별도 상태로 보여준다.
 */
@Composable
private fun PlaceSearchField(
    query: String,
    results: List<PlaceSearchItem>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onResultClick: (PlaceSearchItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        DallimTextField(
            value = query,
            onValueChange = onQueryChange,
            label = "장소 검색",
            placeholder = "예: 안양역",
        )

        when {
            isSearching -> Row(
                modifier = Modifier.padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    color = DallimColors.Primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "검색 중이에요",
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
            query.isNotBlank() && results.isEmpty() -> Text(
                text = "검색 결과가 없어요",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            results.isNotEmpty() -> Column(modifier = Modifier.padding(top = Spacing.sm)) {
                results.forEach { item ->
                    PlaceSearchResultRow(item = item, onClick = { onResultClick(item) })
                }
            }
        }
    }
}

@Composable
private fun PlaceSearchResultRow(item: PlaceSearchItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DallimShapes.CardCorner)
            .background(DallimColors.Surface)
            .clickable { onClick() }
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = DallimColors.TextSecondary,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.padding(start = Spacing.sm)) {
            Text(text = item.name, style = DallimTypography.Body, color = DallimColors.TextPrimary)
            Text(
                text = item.address,
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/** SHAPE 모드에서 고를 수 있는 등록된 모양 (docs/02-api-spec.md 13.3, STAR는 v1 제외). */
private enum class ShapeOption(val apiValue: String, val label: String) {
    HEART("HEART", "하트"),
    CIRCLE("CIRCLE", "원"),
    DROP("DROP", "물방울"),
}

/**
 * 모양 템플릿의 절대 크기 프리셋 — 거리는 이 크기의 결과값이지 입력이 아니다
 * (docs/02-api-spec.md 13.1.1). 표시하는 거리 범위는 하트/원/물방울 실측 기준 "약"이다.
 */
private enum class SizeOption(val apiValue: String, val label: String, val distanceRange: String, val note: String?) {
    S("S", "짧은 코스 (S)", "약 9~10km", null),
    M("M", "보통 코스 (M)", "약 14~18km", null),
    L("L", "긴 코스 (L)", "약 20~25km", "하프마라톤급, 상급자용"),
}

/**
 * "모양 선택" 코스 방식 (docs/02-api-spec.md 13장) — 등록된 3개 모양을 아이콘 그리드로, 크기는
 * S/M/L 프리셋으로 고른다(13.5). 이 화면 안에서 간단한 Canvas 도형으로 아이콘을 직접 그린다
 * (정교한 에셋은 이번 범위 밖) — [RouteThumbnailView]와 달리 서버 GeoJSON이 아닌 고정된 3종
 * 템플릿 미리보기라 제네릭 클립아트 금지 규칙과는 무관하다.
 */
@Composable
private fun ShapeSection(
    shapeType: String?,
    shapeSize: String,
    onShapeTypeSelected: (String) -> Unit,
    onShapeSizeSelected: (String) -> Unit,
) {
    Text(
        text = "모양",
        style = DallimTypography.Title2,
        color = DallimColors.TextPrimary,
        modifier = Modifier.padding(top = Spacing.lg),
    )
    Text(
        text = "원하는 모양의 윤곽을 따라 달리는 코스를 만들어드려요",
        style = DallimTypography.Caption,
        color = DallimColors.TextSecondary,
        modifier = Modifier.padding(top = Spacing.xs),
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ShapeOption.entries.forEach { option ->
            ShapeIconButton(
                option = option,
                selected = shapeType == option.apiValue,
                onClick = { onShapeTypeSelected(option.apiValue) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Text(
        text = "크기",
        style = DallimTypography.Title2,
        color = DallimColors.TextPrimary,
        modifier = Modifier.padding(top = Spacing.lg),
    )
    Text(
        text = "모양의 크기를 고르면 결과 거리가 정해져요 (13.1.1 실측 기준, 도로 사정에 따라 달라질 수 있어요)",
        style = DallimTypography.Caption,
        color = DallimColors.TextSecondary,
        modifier = Modifier.padding(top = Spacing.xs),
    )
    Column(
        modifier = Modifier.padding(top = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SizeOption.entries.forEach { option ->
            SizeOptionRow(
                option = option,
                selected = shapeSize == option.apiValue,
                onClick = { onShapeSizeSelected(option.apiValue) },
            )
        }
    }
}

@Composable
private fun SizeOptionRow(
    option: SizeOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) DallimColors.Primary else DallimColors.TextPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DallimShapes.CardCorner)
            .background(if (selected) DallimColors.PrimaryLight else DallimColors.Surface)
            .clickable(onClick = onClick)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = option.label, style = DallimTypography.Body, color = contentColor)
            Text(
                text = if (option.note != null) "${option.distanceRange} · ${option.note}" else option.distanceRange,
                style = DallimTypography.Caption,
                color = if (selected) DallimColors.Primary else DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun ShapeIconButton(
    option: ShapeOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glyphColor = if (selected) DallimColors.Primary else DallimColors.TextSecondary
    Column(
        modifier = modifier
            .clip(DallimShapes.CardCorner)
            .background(if (selected) DallimColors.PrimaryLight else DallimColors.Surface)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(modifier = Modifier.size(40.dp)) {
            drawShapeGlyph(option = option, color = glyphColor)
        }
        Text(
            text = option.label,
            style = DallimTypography.Caption,
            color = glyphColor,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/** 4종 모양 아이콘을 Canvas 도형으로 직접 그린다 — 제네릭 아이콘/클립아트 사용 금지. */
private fun DrawScope.drawShapeGlyph(option: ShapeOption, color: Color) {
    val strokeWidth = size.minDimension * 0.09f
    val stroke = Stroke(width = strokeWidth, join = StrokeJoin.Round, cap = StrokeCap.Round)
    when (option) {
        ShapeOption.CIRCLE -> drawCircle(
            color = color,
            radius = size.minDimension / 2f - strokeWidth,
            center = center,
            style = stroke,
        )
        ShapeOption.HEART -> drawPath(path = heartGlyphPath(size), color = color, style = stroke)
        ShapeOption.DROP -> drawPath(path = dropGlyphPath(size), color = color, style = stroke)
    }
}

/** 표준 하트 매개변수 곡선(x = 16sin³t, y = 13cos t − 5cos2t − 2cos3t − cos4t)을 샘플링해 정규화. */
private fun heartGlyphPath(size: Size): Path {
    val steps = 48
    val rawPoints = (0..steps).map { i ->
        val t = (i.toFloat() / steps) * (2f * PI.toFloat())
        val x = 16f * sin(t).let { it * it * it }
        val y = -(13f * cos(t) - 5f * cos(2f * t) - 2f * cos(3f * t) - cos(4f * t))
        Offset(x, y)
    }
    return rawPoints.toNormalizedPath(size, marginFraction = 0.08f)
}

/** 위쪽이 뾰족하고 아래쪽이 둥근 물방울 — 큐빅 베지어 2개로 좌우 대칭 윤곽을 그린다. */
private fun dropGlyphPath(size: Size): Path {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    return Path().apply {
        moveTo(cx, h * 0.06f)
        cubicTo(cx + w * 0.46f, h * 0.38f, cx + w * 0.38f, h * 0.94f, cx, h * 0.94f)
        cubicTo(cx - w * 0.38f, h * 0.94f, cx - w * 0.46f, h * 0.38f, cx, h * 0.06f)
        close()
    }
}

/** 임의의 점 목록을 [size] 안에 [marginFraction] 여백을 두고 중앙 정렬해 닫힌 Path로 변환한다. */
private fun List<Offset>.toNormalizedPath(size: Size, marginFraction: Float): Path {
    val minX = minOf { it.x }
    val maxX = maxOf { it.x }
    val minY = minOf { it.y }
    val maxY = maxOf { it.y }
    val margin = 1f - marginFraction * 2f
    val scale = minOf(size.width * margin / (maxX - minX), size.height * margin / (maxY - minY))
    val offsetX = size.width / 2f - (minX + maxX) / 2f * scale
    val offsetY = size.height / 2f - (minY + maxY) / 2f * scale
    return Path().apply {
        forEachIndexed { index, point ->
            val x = point.x * scale + offsetX
            val y = point.y * scale + offsetY
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
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
                description = when (uiState.mode) {
                    "POINT_TO_POINT" -> "현재 위치에서 출발해 지정한 목적지까지 가는 코스를 만들어드려요."
                    "SHAPE" -> "선택한 모양의 윤곽을 따라 달리는 코스를 만들어드려요."
                    else -> "현재 위치에서 출발해 다시 돌아오는 코스를 만들어드려요."
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
            onPlaceSearchQueryChange = {},
            onPlaceSearchResultClick = {},
            onModeSelected = {},
            onDestinationLongPress = {},
            onDestinationClearClick = {},
            onShapeTypeSelected = {},
            onShapeSizeSelected = {},
            onGenerateClick = {},
            onSaveClick = {},
            onSnackbarShown = {},
            onQuotaExceededModalDismiss = {},
            onWatchAdClick = {},
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
            onPlaceSearchQueryChange = {},
            onPlaceSearchResultClick = {},
            onModeSelected = {},
            onDestinationLongPress = {},
            onDestinationClearClick = {},
            onShapeTypeSelected = {},
            onShapeSizeSelected = {},
            onGenerateClick = {},
            onSaveClick = {},
            onSnackbarShown = {},
            onQuotaExceededModalDismiss = {},
            onWatchAdClick = {},
        )
    }
}
