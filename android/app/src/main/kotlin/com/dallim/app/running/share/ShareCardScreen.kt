package com.dallim.app.running.share

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.running.RunFormat
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.run.RunDetailResponseBody
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimFilterChip
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimGradient
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S-26 공유 카드 편집 (docs/01-feature-spec.md §1.3) — 배경/비율/표시 항목을 고른 뒤
 * `Canvas -> Bitmap -> ShareSheet`([ShareCardRenderer]) 로 로컬 합성해 공유한다.
 */
@Composable
fun ShareCardRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShareCardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }

    ShareCardScreen(
        uiState = uiState,
        isSharing = isSharing,
        shareError = shareError,
        onBackClick = onBackClick,
        onRetryClick = viewModel::load,
        onBackgroundSelected = viewModel::onBackgroundSelected,
        onRatioSelected = viewModel::onRatioSelected,
        onToggleDistance = viewModel::onToggleDistance,
        onToggleDuration = viewModel::onToggleDuration,
        onTogglePace = viewModel::onTogglePace,
        onShareClick = { run, options ->
            isSharing = true
            shareError = null
            scope.launch {
                runCatching {
                    val bitmap = withContext(Dispatchers.Default) {
                        ShareCardRenderer.render(
                            options = options,
                            actualRoute = run.actualGeoJson.toGeoPoints(),
                            // 자유 러닝(2026-09-26, routeId == null)은 코스 이름이 없다.
                            routeName = run.routeName ?: "자유 러닝",
                            routeEmoji = "🐳",
                            distanceKm = run.distanceKm,
                            durationSeconds = run.durationSeconds,
                            paceSecPerKm = run.averagePaceSecPerKm,
                        )
                    }
                    val uri = ShareCardRenderer.saveToCacheAndGetUri(context, bitmap)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "달림 공유하기"))
                }.onFailure {
                    shareError = "공유 카드를 만들지 못했어요. 다시 시도해주세요."
                }
                isSharing = false
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun ShareCardScreen(
    uiState: ShareCardUiState,
    isSharing: Boolean,
    shareError: String?,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onBackgroundSelected: (ShareCardBackground) -> Unit,
    onRatioSelected: (ShareCardRatio) -> Unit,
    onToggleDistance: () -> Unit,
    onToggleDuration: () -> Unit,
    onTogglePace: () -> Unit,
    onShareClick: (RunDetailResponseBody, ShareCardOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기", tint = DallimColors.TextPrimary)
            }
        }

        when (uiState) {
            is ShareCardUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
            is ShareCardUiState.Error -> DallimErrorState(
                title = "결과를 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            is ShareCardUiState.Ready -> {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.ScreenHorizontal)) {
                    SharePreviewCard(run = uiState.run, options = uiState.options, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md))

                    Text(text = "배경", style = DallimTypography.Title2, color = DallimColors.TextPrimary, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        DallimFilterChip(label = "그라디언트", selected = uiState.options.background == ShareCardBackground.GRADIENT, onClick = { onBackgroundSelected(ShareCardBackground.GRADIENT) })
                        DallimFilterChip(label = "라이트", selected = uiState.options.background == ShareCardBackground.LIGHT, onClick = { onBackgroundSelected(ShareCardBackground.LIGHT) })
                        DallimFilterChip(label = "다크", selected = uiState.options.background == ShareCardBackground.DARK, onClick = { onBackgroundSelected(ShareCardBackground.DARK) })
                    }

                    Text(text = "비율", style = DallimTypography.Title2, color = DallimColors.TextPrimary, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        ShareCardRatio.entries.forEach { ratio ->
                            DallimFilterChip(label = ratio.label, selected = uiState.options.ratio == ratio, onClick = { onRatioSelected(ratio) })
                        }
                    }

                    Text(text = "표시 항목", style = DallimTypography.Title2, color = DallimColors.TextPrimary, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        DallimFilterChip(label = "거리", selected = uiState.options.showDistance, onClick = onToggleDistance)
                        DallimFilterChip(label = "시간", selected = uiState.options.showDuration, onClick = onToggleDuration)
                        DallimFilterChip(label = "페이스", selected = uiState.options.showPace, onClick = onTogglePace)
                    }

                    if (shareError != null) {
                        Text(text = shareError, style = DallimTypography.Caption, color = DallimColors.Error, modifier = Modifier.padding(top = Spacing.lg))
                    }

                    Box(modifier = Modifier.padding(bottom = Spacing.xxl))
                }

                DallimPrimaryButton(
                    text = if (isSharing) "만드는 중..." else "공유하기",
                    onClick = { onShareClick(uiState.run, uiState.options) },
                    enabled = !isSharing,
                    modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md),
                )
            }
        }
    }
}

/** [ShareCardRenderer]가 만드는 최종 Bitmap 레이아웃을 Compose로 근사한 실시간 미리보기. */
@Composable
private fun SharePreviewCard(run: RunDetailResponseBody, options: ShareCardOptions, modifier: Modifier = Modifier) {
    val isDark = options.background != ShareCardBackground.LIGHT
    val contentColor = if (isDark) DallimColors.Surface else DallimColors.TextPrimary
    val secondaryColor = if (isDark) DallimColors.Surface.copy(alpha = 0.7f) else DallimColors.TextSecondary

    Box(
        modifier = modifier
            .aspectRatio(options.ratio.widthPx.toFloat() / options.ratio.heightPx.toFloat())
            .clip(RoundedCornerShape(20.dp))
            .background(
                when (options.background) {
                    ShareCardBackground.LIGHT -> SolidColor(DallimColors.Background)
                    ShareCardBackground.DARK -> SolidColor(DallimColors.BackgroundDark)
                    ShareCardBackground.GRADIENT -> DallimGradient
                },
            )
            .padding(Spacing.lg),
    ) {
        val coordinates = run.actualGeoJson.toGeoPoints()
        if (coordinates.size >= 2) {
            Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.55f)) {
                val padX = size.width * 0.05f
                val padY = size.height * 0.05f
                val drawW = size.width - 2 * padX
                val drawH = size.height - 2 * padY
                val lngs = coordinates.map { it.lng }
                val lats = coordinates.map { it.lat }
                val minLng = lngs.min(); val maxLng = lngs.max()
                val minLat = lats.min(); val maxLat = lats.max()
                val lngRange = (maxLng - minLng).takeIf { it > 0.0 } ?: 1.0
                val latRange = (maxLat - minLat).takeIf { it > 0.0 } ?: 1.0
                fun project(p: GeoPoint): Offset {
                    val nx = ((p.lng - minLng) / lngRange).toFloat()
                    val ny = ((p.lat - minLat) / latRange).toFloat()
                    return Offset(padX + nx * drawW, padY + (1f - ny) * drawH)
                }
                val path = Path().apply {
                    val first = project(coordinates.first())
                    moveTo(first.x, first.y)
                    coordinates.drop(1).forEach { lineTo(project(it).x, project(it).y) }
                }
                drawPath(path = path, brush = DallimGradient, style = Stroke(width = size.width * 0.012f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(text = "🐳 ${run.routeName}", style = DallimTypography.Title1, color = contentColor, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (options.showDistance) Text(text = "${RunFormat.km(run.distanceKm)}km", style = DallimTypography.Title1, color = contentColor)
                if (options.showDuration) Text(text = RunFormat.duration(run.durationSeconds.toLong()), style = DallimTypography.Title1, color = contentColor)
                if (options.showPace) Text(text = "${RunFormat.pace(run.averagePaceSecPerKm)}/km", style = DallimTypography.Title1, color = contentColor)
            }
            Text(text = "달림 · Dallim", style = DallimTypography.Caption, color = secondaryColor, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
}

private fun GeoJsonLineString.toGeoPoints(): List<GeoPoint> =
    toLngLatPairs().map { (lng, lat) -> GeoPoint(lng = lng, lat = lat) }

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun ShareCardScreenPreview() {
    DallimTheme {
        ShareCardScreen(
            uiState = ShareCardUiState.Ready(
                run = RunDetailResponseBody(
                    runId = "run_301",
                    routeId = "rt_001",
                    routeName = "고래",
                    status = "COMPLETED",
                    actualGeoJson = GeoJsonLineString(
                        coordinates = listOf(listOf(127.05, 37.25), listOf(127.052, 37.253), listOf(127.058, 37.256)),
                    ),
                    plannedGeoJson = GeoJsonLineString(),
                    distanceKm = 5.18,
                    durationSeconds = 2078,
                    averagePaceSecPerKm = 401,
                    sketchMatchPercent = 92,
                    routeCompletionPercent = 97,
                    completedAt = "2026-08-23T09:34:38Z",
                ),
            ),
            isSharing = false,
            shareError = null,
            onBackClick = {},
            onRetryClick = {},
            onBackgroundSelected = {},
            onRatioSelected = {},
            onToggleDistance = {},
            onToggleDuration = {},
            onTogglePace = {},
            onShareClick = { _, _ -> },
        )
    }
}
