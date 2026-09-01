package com.dallim.app.onboarding.firstroute

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.route.RouteListItem
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.RouteThumbnailView
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.Spacing

/**
 * S-06 첫 코스 제안 — 현재 위치 기준 1.5~2.5km 코스 1개 표시.
 * [지금 달리기] -> S-20(러닝 준비) / [나중에] -> S-10(홈).
 */
@Composable
fun FirstRouteSuggestionRoute(
    onStartRunClick: (routeId: String) -> Unit,
    onLaterClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FirstRouteSuggestionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FirstRouteSuggestionScreen(
        uiState = uiState,
        onStartRunClick = onStartRunClick,
        onLaterClick = onLaterClick,
        onRetryClick = viewModel::loadSuggestion,
        modifier = modifier,
    )
}

@Composable
private fun FirstRouteSuggestionScreen(
    uiState: FirstRouteSuggestionUiState,
    onStartRunClick: (routeId: String) -> Unit,
    onLaterClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.ScreenHorizontal),
    ) {
        Text(
            text = "첫 코스를 준비했어요",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.xxl, bottom = Spacing.xl),
        )

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            when (uiState) {
                is FirstRouteSuggestionUiState.Loading -> LoadingContent()
                is FirstRouteSuggestionUiState.Success -> RouteContent(uiState.route)
                is FirstRouteSuggestionUiState.Empty -> MessageContent(
                    title = "근처에 추천할 코스가 아직 없어요",
                    body = "다른 지역에서 코스를 둘러보거나 나중에 다시 확인해주세요.",
                )
                is FirstRouteSuggestionUiState.Error -> MessageContent(
                    title = "코스를 불러오지 못했어요",
                    body = "잠시 후 다시 시도해주세요.",
                    onRetryClick = onRetryClick,
                )
            }
        }

        val canStartRun = uiState is FirstRouteSuggestionUiState.Success
        DallimPrimaryButton(
            text = "지금 달리기",
            onClick = { if (uiState is FirstRouteSuggestionUiState.Success) onStartRunClick(uiState.route.routeId) },
            enabled = canStartRun,
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DallimTextButton(text = "나중에", onClick = onLaterClick)
        }
        Box(modifier = Modifier.padding(bottom = Spacing.xl))
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = DallimColors.Primary)
        Text(
            text = "주변 코스를 찾는 중이에요",
            fontSize = 14.sp,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.md),
        )
    }
}

@Composable
private fun RouteContent(route: RouteListItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        RouteThumbnailView(
            coordinates = route.thumbnailGeoJson.toGeoPoints(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 코스 이름의 이모지는 콘텐츠 데이터이므로 예외적으로 허용된다 (docs/04-ui-guide.md §7).
            Text(text = route.emoji, fontSize = 24.sp)
            Text(
                text = route.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = DallimColors.TextPrimary,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        Text(
            text = "${route.distanceKm}km · 약 ${route.estimatedMinutes}분",
            fontSize = 14.sp,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun MessageContent(
    title: String,
    body: String,
    onRetryClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = DallimColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            fontSize = 14.sp,
            color = DallimColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        if (onRetryClick != null) {
            DallimTextButton(text = "다시 시도", onClick = onRetryClick, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

private fun GeoJsonLineString.toGeoPoints(): List<GeoPoint> =
    toLngLatPairs().map { (lng, lat) -> GeoPoint(lng = lng, lat = lat) }

@Preview(showBackground = true)
@Composable
private fun FirstRouteSuggestionScreenPreview() {
    DallimTheme {
        FirstRouteSuggestionScreen(
            uiState = FirstRouteSuggestionUiState.Success(
                RouteListItem(
                    routeId = "rt_001",
                    name = "고래",
                    emoji = "🐳",
                    distanceKm = 5.1,
                    estimatedMinutes = 36,
                    status = "POPULAR",
                    finisherCount = 148,
                    thumbnailGeoJson = GeoJsonLineString(
                        coordinates = listOf(
                            listOf(127.05, 37.25),
                            listOf(127.052, 37.253),
                            listOf(127.055, 37.251),
                            listOf(127.058, 37.256),
                        ),
                    ),
                ),
            ),
            onStartRunClick = { _ -> },
            onLaterClick = {},
            onRetryClick = {},
        )
    }
}
