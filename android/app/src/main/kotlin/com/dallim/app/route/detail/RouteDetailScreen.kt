package com.dallim.app.route.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.running.RunFormat
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.route.FinisherThumbnail
import com.dallim.network.route.RouteDetailResponseBody
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.RouteStatusBadge
import com.dallim.ui.components.RouteThumbnailView
import com.dallim.ui.components.toRouteStatus
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-16 Route 상세 — 코스 지도/스펙/러너 GPS 썸네일 (docs/01-feature-spec.md §1.2).
 *
 * "코스 지도" 영역: 네이버맵/카카오맵 SDK 실연동은 API 키·Gradle 의존성이 아직 설정되지 않아
 * 이번 라운드 범위 밖이다. 대신 [RouteThumbnailView]로 서버 GeoJSON을 정적 렌더링해 대체하며,
 * 인터랙티브 지도 연동은 후속 작업으로 보류한다 — SPEC 축소가 아니라 환경 제약이다.
 *
 * MVP1은 소셜/대회를 범위에서 제외하므로(CLAUDE.md) 하단 CTA는 "혼자 달리기" 하나만 둔다.
 */
@Composable
fun RouteDetailRoute(
    onBackClick: () -> Unit,
    onStartRunClick: (routeId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RouteDetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onToggleSaveClick = viewModel::onToggleSave,
        onStartRunClick = onStartRunClick,
        onRetryClick = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun RouteDetailScreen(
    uiState: RouteDetailUiState,
    onBackClick: () -> Unit,
    onToggleSaveClick: () -> Unit,
    onStartRunClick: (String) -> Unit,
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = DallimColors.TextPrimary,
                )
            }
            if (uiState is RouteDetailUiState.Success) {
                IconButton(onClick = onToggleSaveClick, enabled = !uiState.isSaving) {
                    Icon(
                        imageVector = if (uiState.route.isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (uiState.route.isSaved) "저장 취소" else "코스 저장",
                        tint = DallimColors.Primary,
                    )
                }
            }
        }

        when (uiState) {
            is RouteDetailUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
            is RouteDetailUiState.Error -> DallimErrorState(
                title = "코스를 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            is RouteDetailUiState.Success -> {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    RouteThumbnailView(
                        coordinates = uiState.route.geoJson.toGeoPoints(),
                        useGradient = true,
                        cornerRadius = 0.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Column(modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal)) {
                        Row(
                            modifier = Modifier.padding(top = Spacing.lg),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 코스 이름의 이모지는 콘텐츠 데이터이므로 예외적으로 허용된다 (docs/04-ui-guide.md §7).
                            Text(text = uiState.route.emoji, fontSize = 28.sp)
                            Text(
                                text = uiState.route.name,
                                style = DallimTypography.Title1,
                                color = DallimColors.TextPrimary,
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        }
                        Row(
                            modifier = Modifier.padding(top = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RouteStatusBadge(status = uiState.route.status.toRouteStatus())
                            Text(
                                text = "${uiState.route.finisherCount}명 완주",
                                style = DallimTypography.Caption,
                                color = DallimColors.TextSecondary,
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }

                        SpecGrid(route = uiState.route, modifier = Modifier.padding(top = Spacing.xl))

                        if (uiState.route.topFeedbackTags.isNotEmpty()) {
                            Text(
                                text = "러너 반응",
                                style = DallimTypography.Title2,
                                color = DallimColors.TextPrimary,
                                modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
                            )
                            FeedbackTagRow(tags = uiState.route.topFeedbackTags)
                        }

                        if (uiState.finishers.isNotEmpty()) {
                            Text(
                                text = "러너들의 기록",
                                style = DallimTypography.Title2,
                                color = DallimColors.TextPrimary,
                                modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
                            )
                            FinishersRow(finishers = uiState.finishers)
                        }

                        if (uiState.saveErrorMessage != null) {
                            Text(
                                text = uiState.saveErrorMessage,
                                style = DallimTypography.Caption,
                                color = DallimColors.Error,
                                modifier = Modifier.padding(top = Spacing.md),
                            )
                        }

                        Box(modifier = Modifier.padding(bottom = Spacing.xxl))
                    }
                }

                DallimPrimaryButton(
                    text = "혼자 달리기",
                    onClick = { onStartRunClick(uiState.route.routeId) },
                    modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun SpecGrid(route: RouteDetailResponseBody, modifier: Modifier = Modifier) {
    val specs = listOf(
        "거리" to "${RunFormat.km(route.distanceKm)}km",
        "예상 시간" to "약 ${route.estimatedMinutes}분",
        "난이도" to route.difficulty.toDifficultyLabel(),
        "신호등" to "${route.trafficLightCount}개",
        "고도 상승" to "${route.elevationGainM}m",
        "반복 구간" to "${route.repeatSegmentPercent}%",
        "러너빌리티" to "${(route.runability * 100).toInt()}점",
        "완주자 수" to "${route.finisherCount}명",
    )
    Column(modifier = modifier) {
        specs.chunked(2).forEach { rowSpecs ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                rowSpecs.forEach { (label, value) -> SpecCell(label = label, value = value, modifier = Modifier.weight(1f)) }
                if (rowSpecs.size < 2) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SpecCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = DallimTypography.Caption, color = DallimColors.TextSecondary)
        Text(
            text = value,
            style = DallimTypography.Title2,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun FeedbackTagRow(tags: List<String>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(tags) { tag ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(DallimColors.PrimaryLight)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                Text(text = tag, style = DallimTypography.Caption, color = DallimColors.Primary)
            }
        }
    }
}

@Composable
private fun FinishersRow(finishers: List<FinisherThumbnail>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(bottom = Spacing.xs),
    ) {
        items(finishers) { finisher ->
            Column(modifier = Modifier.width(88.dp)) {
                RouteThumbnailView(
                    coordinates = finisher.thumbnailGeoJson.toGeoPoints(),
                    modifier = Modifier.aspectRatio(1f),
                )
                Text(
                    text = finisher.userNickname,
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

private fun String.toDifficultyLabel(): String = when (this) {
    "EASY" -> "쉬움"
    "MEDIUM" -> "보통"
    "HARD" -> "어려움"
    else -> this
}

private fun GeoJsonLineString.toGeoPoints(): List<GeoPoint> =
    toLngLatPairs().map { (lng, lat) -> GeoPoint(lng = lng, lat = lat) }

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun RouteDetailScreenPreview() {
    DallimTheme {
        RouteDetailScreen(
            uiState = RouteDetailUiState.Success(
                route = RouteDetailResponseBody(
                    routeId = "rt_001",
                    name = "고래",
                    emoji = "🐳",
                    geoJson = GeoJsonLineString(
                        coordinates = listOf(
                            listOf(127.05, 37.25),
                            listOf(127.052, 37.253),
                            listOf(127.055, 37.251),
                            listOf(127.058, 37.256),
                        ),
                    ),
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
                    topFeedbackTags = listOf("그림이 잘 보여요", "러닝하기 편해요"),
                ),
                finishers = listOf(
                    FinisherThumbnail(
                        runId = "run_205",
                        userNickname = "숲속러너",
                        thumbnailGeoJson = GeoJsonLineString(
                            coordinates = listOf(listOf(127.05, 37.25), listOf(127.06, 37.26)),
                        ),
                    ),
                ),
            ),
            onBackClick = {},
            onToggleSaveClick = {},
            onStartRunClick = {},
            onRetryClick = {},
        )
    }
}
