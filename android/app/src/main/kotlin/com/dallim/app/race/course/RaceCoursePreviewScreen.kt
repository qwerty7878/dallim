package com.dallim.app.race.course

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.BuildConfig
import com.dallim.app.race.RaceFormat
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.race.RaceCourseResponseBody
import com.dallim.network.race.RaceCourseSegmentItem
import com.dallim.ui.components.DallimCard
import com.dallim.ui.components.DallimEmptyState
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimSecondaryButton
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.NaverRouteMapView
import com.dallim.ui.components.RouteThumbnailView
import com.dallim.ui.components.SectionHeader
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-85 대회 코스 미리 달리기 (docs/02-api-spec.md 16.6 신규,
 * docs/달림_화면별_상세기획서_v1.3.md PART 3-H). 대회 상세(S-81)에서 "공식 코스"가 있을 때만
 * 진입한다. 구간별 "이 구간 달리기"는 그 구간의 `routeId`로 기존 S-20(러닝 준비)을 그대로
 * 재사용한다 — 새 러닝 진입 로직을 만들지 않는다.
 */
@Composable
fun RaceCoursePreviewRoute(
    onBackClick: () -> Unit,
    onRunSegmentClick: (routeId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RaceCoursePreviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RaceCoursePreviewScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetryClick = viewModel::load,
        onRunSegmentClick = onRunSegmentClick,
        modifier = modifier,
    )
}

@Composable
private fun RaceCoursePreviewScreen(
    uiState: RaceCoursePreviewUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onRunSegmentClick: (routeId: String) -> Unit,
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
            Text(text = "코스 미리 달리기", style = DallimTypography.Title1, color = DallimColors.TextPrimary)
        }

        when (uiState) {
            is RaceCoursePreviewUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
            is RaceCoursePreviewUiState.Error -> DallimErrorState(
                title = "코스 정보를 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            is RaceCoursePreviewUiState.Success -> {
                if (!uiState.course.hasCourse) {
                    DallimEmptyState(
                        title = "이 대회는 아직 공식 코스가 없어요",
                        description = "코스가 등록되면 이 화면에서 미리 달려볼 수 있어요.",
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    CourseContent(
                        course = uiState.course,
                        onRunSegmentClick = onRunSegmentClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseContent(
    course: RaceCourseResponseBody,
    onRunSegmentClick: (routeId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coordinates = course.geoJson?.toGeoPoints().orEmpty()

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        if (BuildConfig.NAVER_MAP_CLIENT_ID_CONFIGURED) {
            NaverRouteMapView(
                plannedRoute = coordinates,
                actualRoute = emptyList(),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
        } else {
            RouteThumbnailView(
                coordinates = coordinates,
                useGradient = true,
                cornerRadius = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal)) {
            Row(
                modifier = Modifier.padding(top = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                CourseStat(label = "총 거리", value = RaceFormat.distanceLabel(course.distanceKm))
                CourseStat(label = "고도", value = course.elevationGainM?.let { RaceFormat.elevationLabel(it) } ?: "-")
            }

            val previewProgressPercent = course.previewProgressPercent
            if (previewProgressPercent != null) {
                CoursePreviewProgress(
                    previewProgressPercent = previewProgressPercent,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            }

            SectionHeader(title = "구간", modifier = Modifier.padding(top = Spacing.xl))
        }

        course.segments.sortedBy { it.orderIndex }.forEach { segment ->
            SegmentCard(
                segment = segment,
                onRunClick = { onRunSegmentClick(segment.routeId) },
                modifier = Modifier.padding(
                    start = Spacing.ScreenHorizontal,
                    end = Spacing.ScreenHorizontal,
                    bottom = Spacing.md,
                ),
            )
        }

        Box(modifier = Modifier.padding(bottom = Spacing.xxl))
    }
}

@Composable
private fun CourseStat(label: String, value: String, modifier: Modifier = Modifier) {
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

/** "OO% 답사 완료" — 서버가 내려주는 [RaceCourseResponseBody.previewProgressPercent]를 그대로 표시만 한다. */
@Composable
private fun CoursePreviewProgress(previewProgressPercent: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = RaceFormat.previewProgressLabel(previewProgressPercent),
            style = DallimTypography.Body,
            color = DallimColors.TextPrimary,
        )
        Box(
            modifier = Modifier
                .padding(top = Spacing.xs)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DallimColors.Border),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(previewProgressPercent.coerceIn(0, 100) / 100f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(DallimColors.Primary),
            )
        }
    }
}

@Composable
private fun SegmentCard(
    segment: RaceCourseSegmentItem,
    onRunClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DallimCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = segment.label,
                style = DallimTypography.Body,
                color = DallimColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (segment.isCompleted) {
                SegmentCompletedBadge()
            }
        }

        Row(
            modifier = Modifier.padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "${RaceFormat.distanceLabel(segment.distanceKm)} · ${RaceFormat.estimatedMinutesLabel(segment.estimatedMinutes)} · ${RaceFormat.elevationLabel(segment.elevationGainM)}",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
            )
        }

        DallimSecondaryButton(
            text = "이 구간 달리기",
            onClick = onRunClick,
            modifier = Modifier.padding(top = Spacing.md),
        )
    }
}

@Composable
private fun SegmentCompletedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DallimColors.Success.copy(alpha = 0.15f))
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    ) {
        Text(text = "완주", style = DallimTypography.Caption, color = DallimColors.Success)
    }
}

private fun GeoJsonLineString.toGeoPoints(): List<GeoPoint> =
    toLngLatPairs().map { (lng, lat) -> GeoPoint(lng = lng, lat = lat) }

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun RaceCoursePreviewScreenPreview() {
    DallimTheme {
        RaceCoursePreviewScreen(
            uiState = RaceCoursePreviewUiState.Success(
                course = RaceCourseResponseBody(
                    hasCourse = true,
                    geoJson = GeoJsonLineString(
                        coordinates = listOf(
                            listOf(127.108, 37.402),
                            listOf(127.110, 37.404),
                            listOf(127.113, 37.403),
                            listOf(127.115, 37.406),
                        ),
                    ),
                    distanceKm = 21.0975,
                    elevationGainM = 128,
                    previewProgressPercent = 62,
                    segments = listOf(
                        RaceCourseSegmentItem(
                            segmentId = "seg_1",
                            label = "잠실종합운동장 → 탄천 교차로",
                            routeId = "rt_101",
                            distanceKm = 5.0,
                            estimatedMinutes = 30,
                            elevationGainM = 12,
                            orderIndex = 0,
                            isCompleted = true,
                        ),
                        RaceCourseSegmentItem(
                            segmentId = "seg_2",
                            label = "탄천 교차로 → 반포대교 남단",
                            routeId = "rt_102",
                            distanceKm = 8.0,
                            estimatedMinutes = 48,
                            elevationGainM = 40,
                            orderIndex = 1,
                            isCompleted = false,
                        ),
                        RaceCourseSegmentItem(
                            segmentId = "seg_3",
                            label = "반포대교 남단 → 여의도 공원",
                            routeId = "rt_103",
                            distanceKm = 8.0975,
                            estimatedMinutes = 49,
                            elevationGainM = 76,
                            orderIndex = 2,
                            isCompleted = false,
                        ),
                    ),
                ),
            ),
            onBackClick = {},
            onRetryClick = {},
            onRunSegmentClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun RaceCoursePreviewScreenEmptyPreview() {
    DallimTheme {
        RaceCoursePreviewScreen(
            uiState = RaceCoursePreviewUiState.Success(course = RaceCourseResponseBody(hasCourse = false)),
            onBackClick = {},
            onRetryClick = {},
            onRunSegmentClick = {},
        )
    }
}
