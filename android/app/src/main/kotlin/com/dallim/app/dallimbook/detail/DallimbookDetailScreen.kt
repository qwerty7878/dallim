package com.dallim.app.dallimbook.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.running.RunFormat
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.run.RunDetailResponseBody
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.RunResultCanvas
import com.dallim.ui.components.RunStatusBadge
import com.dallim.ui.components.toRunStatus
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-41 작품 상세 (docs/01-feature-spec.md §1.4) — 달림북 그리드에서 연 개별 Run 상세.
 * 지표(거리/시간/페이스/Match/커버리지)는 [com.dallim.app.running.result.RunResultScreen]과
 * 완전히 동일한 서버 응답([RunDetailResponseBody])을 그대로 보여준다(CLAUDE.md rule 3).
 * GPS 그림은 [RunResultCanvas]를 재사용하되, 처음 진입 시에는 정적으로 보여주고
 * [리플레이] 버튼을 눌러야만 Path-drawing 애니메이션이 다시 재생된다.
 */
@Composable
fun DallimbookDetailRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DallimbookDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DallimbookDetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetryClick = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun DallimbookDetailScreen(
    uiState: DallimbookDetailUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = DallimColors.TextPrimary,
                )
            }
            Text(text = "작품 상세", style = DallimTypography.Title1, color = DallimColors.TextPrimary)
        }

        when (uiState) {
            is DallimbookDetailUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
            is DallimbookDetailUiState.Error -> DallimErrorState(
                title = "작품을 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            is DallimbookDetailUiState.Success -> DetailContent(
                run = uiState.run,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DetailContent(run: RunDetailResponseBody, modifier: Modifier = Modifier) {
    // 0이면 정적으로 완성된 그림을 보여주고, 1 이상이면 [리플레이] 버튼이 눌릴 때마다
    // key()로 RunResultCanvas를 새로 구성해 Path-drawing 애니메이션을 처음부터 다시 재생한다.
    var replayToken by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.padding(top = Spacing.xl)) {
            key(replayToken) {
                RunResultCanvas(
                    coordinates = run.actualGeoJson.toGeoPoints(),
                    animate = replayToken > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xl, start = Spacing.ScreenHorizontal, end = Spacing.ScreenHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${run.distanceKm}km",
                    style = DallimTypography.Display,
                    color = DallimColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "${run.routeName} · ${run.completedAt.toShortDateLabel()}",
                    style = DallimTypography.Body,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MetricColumn(label = "시간", value = RunFormat.duration(run.durationSeconds.toLong()))
                    MetricColumn(label = "페이스", value = "${RunFormat.pace(run.averagePaceSecPerKm)}/km")
                }

                Row(
                    modifier = Modifier.padding(top = Spacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    RunStatusBadge(status = run.status.toRunStatus())
                    Text(
                        text = "Match ${run.sketchMatchPercent}% · 커버리지 ${run.routeCompletionPercent}%",
                        style = DallimTypography.Caption,
                        color = DallimColors.TextSecondary,
                    )
                }

                DallimPrimaryButton(
                    text = "리플레이",
                    onClick = { replayToken += 1 },
                    modifier = Modifier.padding(top = Spacing.xl),
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = DallimTypography.Title1, color = DallimColors.TextPrimary)
        Text(
            text = label,
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

private fun GeoJsonLineString.toGeoPoints(): List<GeoPoint> =
    toLngLatPairs().map { (lng, lat) -> GeoPoint(lng = lng, lat = lat) }

private fun String.toShortDateLabel(): String = runCatching {
    java.time.OffsetDateTime.parse(this).format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"))
}.getOrDefault(take(10))

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun DallimbookDetailScreenPreview() {
    DallimTheme {
        DallimbookDetailScreen(
            uiState = DallimbookDetailUiState.Success(
                run = RunDetailResponseBody(
                    runId = "run_301",
                    routeId = "rt_001",
                    routeName = "고래",
                    status = "COMPLETED",
                    actualGeoJson = GeoJsonLineString(
                        coordinates = listOf(
                            listOf(127.05, 37.25),
                            listOf(127.052, 37.253),
                            listOf(127.055, 37.251),
                            listOf(127.058, 37.256),
                        ),
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
            onBackClick = {},
            onRetryClick = {},
        )
    }
}
