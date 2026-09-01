package com.dallim.app.running.result

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.run.RunDetailResponseBody
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.RunResultCanvas
import com.dallim.ui.components.RunStatusBadge
import com.dallim.ui.components.toRunStatus
import com.dallim.app.running.RunFormat
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-25 달림 결과 (docs/01-feature-spec.md §1.3, docs/04-ui-guide.md §8) — GPS 그림을
 * "액자에 담긴 작품처럼" Path-drawing 애니메이션으로 재생하고, 서버가 최종 계산한 지표를 보여준다.
 * "굿즈"는 수익화 기능으로 MVP1 범위 밖(CLAUDE.md)이라 액션은 공유/저장 두 개만 둔다.
 */
@Composable
fun RunResultRoute(
    onShareClick: (runId: String) -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RunResultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RunResultScreen(
        uiState = uiState,
        onShareClick = { onShareClick(viewModel.runId) },
        onDoneClick = onDoneClick,
        onRetryClick = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun RunResultScreen(
    uiState: RunResultUiState,
    onShareClick: () -> Unit,
    onDoneClick: () -> Unit,
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
        when (uiState) {
            is RunResultUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
            is RunResultUiState.Error -> DallimErrorState(
                title = "결과를 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            is RunResultUiState.Success -> {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    ResultContent(run = uiState.run)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    DallimPrimaryButton(text = "공유하기", onClick = onShareClick, modifier = Modifier.weight(1f))
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DallimTextButton(text = "달림북에 저장하고 닫기", onClick = onDoneClick)
                }
                Box(modifier = Modifier.padding(bottom = Spacing.lg))
            }
        }
    }
}

@Composable
private fun ResultContent(run: RunDetailResponseBody, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = Spacing.xl)) {
        RunResultCanvas(
            coordinates = run.actualGeoJson.toGeoPoints(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl),
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl, start = Spacing.ScreenHorizontal, end = Spacing.ScreenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${run.distanceKm}km",
                style = DallimTypography.Display,
                color = DallimColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "${run.routeName} 그리기 완료",
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

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun RunResultScreenPreview() {
    DallimTheme {
        RunResultScreen(
            uiState = RunResultUiState.Success(
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
            onShareClick = {},
            onDoneClick = {},
            onRetryClick = {},
        )
    }
}
