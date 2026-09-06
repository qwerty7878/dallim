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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Groups
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.BuildConfig
import com.dallim.app.running.RunFormat
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.route.FinisherThumbnail
import com.dallim.network.route.RouteDetailResponseBody
import com.dallim.network.route.ShapeVoteTallyBody
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.components.DallimTextField
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.NaverRouteMapView
import com.dallim.ui.components.RouteStatusBadge
import com.dallim.ui.components.RouteThumbnailView
import com.dallim.ui.components.toRouteStatus
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-16 Route 상세 — 코스 지도/스펙/러너 GPS 썸네일 (docs/01-feature-spec.md §1.2).
 *
 * "코스 지도" 영역: 네이버맵 SDK가 연동되어 있다 ([NaverRouteMapView], 계획 경로만 표시하고
 * `actualRoute`는 비워둔다 — 아직 달리지 않은 코스이므로). 단, NCP Client ID는 발급 전이라
 * `local.properties`의 `NAVER_MAP_CLIENT_ID`가 비어있는 로컬 빌드에서는
 * `BuildConfig.NAVER_MAP_CLIENT_ID_CONFIGURED == false`가 되어 자동으로 [RouteThumbnailView]
 * Canvas 폴백을 쓴다 — 크래시 없이 항상 빌드/실행 가능하다.
 *
 * 하단 [FinishersRow]의 88dp 완주자 GPS 그림 아바타는 지도가 아니라 스타일라이즈드 썸네일이므로
 * 이 폴백 정책과 무관하게 항상 [RouteThumbnailView]를 쓴다 — "제네릭 아이콘 금지" 원칙 유지.
 *
 * MVP1은 소셜/대회를 범위에서 제외하므로(CLAUDE.md) 하단 CTA는 "혼자 달리기" 하나만 둔다 — 단,
 * "같이 달리기 모집"(§1.8)만은 2026-09-05부로 예외로 범위에 포함돼 [MeetupEntryRow] 섹션으로
 * 별도 노출한다(하단 고정 CTA가 아니라 스크롤 본문 안의 진입 섹션, §1.8.1).
 */
@Composable
fun RouteDetailRoute(
    onBackClick: () -> Unit,
    onStartRunClick: (routeId: String) -> Unit,
    onMeetupsClick: (routeId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RouteDetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onToggleSaveClick = viewModel::onToggleSave,
        onStartRunClick = onStartRunClick,
        onMeetupsClick = onMeetupsClick,
        onRetryClick = viewModel::load,
        onVoteClick = viewModel::onVoteClick,
        onVoteDialogDismiss = viewModel::onVoteDialogDismiss,
        onVoteSubmit = viewModel::onVoteSubmit,
        onVoteErrorShown = viewModel::onVoteErrorShown,
        modifier = modifier,
    )
}

@Composable
private fun RouteDetailScreen(
    uiState: RouteDetailUiState,
    onBackClick: () -> Unit,
    onToggleSaveClick: () -> Unit,
    onStartRunClick: (String) -> Unit,
    onMeetupsClick: (String) -> Unit,
    onRetryClick: () -> Unit,
    onVoteClick: () -> Unit,
    onVoteDialogDismiss: () -> Unit,
    onVoteSubmit: (String) -> Unit,
    onVoteErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val voteErrorMessage = (uiState as? RouteDetailUiState.Success)?.voteErrorMessage

    LaunchedEffect(voteErrorMessage) {
        val message = voteErrorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onVoteErrorShown()
    }

    Scaffold(
        modifier = modifier,
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
                    if (BuildConfig.NAVER_MAP_CLIENT_ID_CONFIGURED) {
                        NaverRouteMapView(
                            plannedRoute = uiState.route.geoJson.toGeoPoints(),
                            actualRoute = emptyList(),
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                    } else {
                        RouteThumbnailView(
                            coordinates = uiState.route.geoJson.toGeoPoints(),
                            useGradient = true,
                            cornerRadius = 0.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

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

                        ShapeVoteSection(
                            shapeVotes = uiState.route.shapeVotes,
                            myShapeVote = uiState.route.myShapeVote,
                            onVoteClick = onVoteClick,
                            modifier = Modifier.padding(top = Spacing.lg),
                        )

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

                        // S-47 진입점 (docs/01-feature-spec.md §1.8.1) — "그 코스에 열려 있는
                        // 모집(OPEN, 미래 시각) 개수를 보여주고 탭하면 S-47로 이동". 개수와
                        // 무관하게 섹션 자체는 항상 노출한다(0건이어도 "모집 만들기"로 이어지는
                        // 진입로 역할).
                        Text(
                            text = "같이 뛸 사람 모집",
                            style = DallimTypography.Title2,
                            color = DallimColors.TextPrimary,
                            modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
                        )
                        MeetupEntryRow(
                            openCount = uiState.openMeetupCount,
                            onClick = { onMeetupsClick(uiState.route.routeId) },
                        )

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

                if (uiState.isVoteDialogOpen) {
                    ShapeVoteDialog(
                        initialLabel = uiState.route.myShapeVote.orEmpty(),
                        isSubmitting = uiState.isSubmittingVote,
                        onDismiss = onVoteDialogDismiss,
                        onSubmit = onVoteSubmit,
                    )
                }
            }
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

/**
 * 커뮤니티 투표(모양 맞추기) — "이 코스, 나한텐 이렇게 보여요"를 자유 텍스트로 집계
 * (docs/02-api-spec.md 4장, v1.3 문서 297행 "고래 73% · 물고기 19% / [나도 투표]"). 막대 그래프 없이
 * 상위 후보를 텍스트로만 나열한다(과설계 금지).
 */
@Composable
private fun ShapeVoteSection(
    shapeVotes: List<ShapeVoteTallyBody>,
    myShapeVote: String?,
    onVoteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "커뮤니티 투표",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
        )
        Text(
            text = if (shapeVotes.isNotEmpty()) {
                shapeVotes.joinToString(" · ") { "${it.label} ${it.percent}%" }
            } else {
                "아직 투표가 없어요"
            },
            style = DallimTypography.Body,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        DallimTextButton(
            text = if (myShapeVote != null) "내 투표: $myShapeVote (변경하기)" else "나도 투표",
            onClick = onVoteClick,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/** [ShapeVoteSection]의 "[나도 투표]"/"변경하기" 다이얼로그 — 라벨 1~10자 입력 후 확인. */
@Composable
private fun ShapeVoteDialog(
    initialLabel: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var label by remember { mutableStateOf(initialLabel) }
    val trimmedLength = label.trim().length
    val isValid = trimmedLength in 1..10

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DallimShapes.CardCorner)
                .background(DallimColors.Surface)
                .padding(Spacing.lg),
        ) {
            Text(
                text = "이 코스, 뭘로 보여요?",
                style = DallimTypography.Title2,
                color = DallimColors.TextPrimary,
            )
            DallimTextField(
                value = label,
                onValueChange = { if (it.length <= 10) label = it },
                label = "예: 물고기",
                errorText = if (label.isNotEmpty() && !isValid) "1~10자로 입력해주세요" else null,
                modifier = Modifier.padding(top = Spacing.md),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                horizontalArrangement = Arrangement.End,
            ) {
                DallimTextButton(text = "취소", onClick = onDismiss, enabled = !isSubmitting)
                DallimTextButton(
                    text = "확인",
                    onClick = { onSubmit(label) },
                    enabled = isValid && !isSubmitting,
                    modifier = Modifier.padding(start = Spacing.md),
                )
            }
        }
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

/** S-47(모집 목록) 진입 섹션 — docs/01-feature-spec.md §1.8.1. */
@Composable
private fun MeetupEntryRow(openCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(DallimShapes.CardCorner)
            .background(DallimColors.Surface)
            .clickable { onClick() }
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = Icons.Filled.Groups, contentDescription = null, tint = DallimColors.Primary)
        Text(
            text = if (openCount > 0) "지금 열려 있는 모집 ${openCount}건" else "아직 열려 있는 모집이 없어요",
            style = DallimTypography.Body,
            color = DallimColors.TextPrimary,
            modifier = Modifier.weight(1f).padding(start = Spacing.md),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = DallimColors.TextSecondary,
        )
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
                    shapeVotes = listOf(
                        ShapeVoteTallyBody(label = "고래", percent = 73),
                        ShapeVoteTallyBody(label = "물고기", percent = 19),
                    ),
                    myShapeVote = "고래",
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
                openMeetupCount = 2,
            ),
            onBackClick = {},
            onToggleSaveClick = {},
            onStartRunClick = {},
            onMeetupsClick = {},
            onRetryClick = {},
            onVoteClick = {},
            onVoteDialogDismiss = {},
            onVoteSubmit = {},
            onVoteErrorShown = {},
        )
    }
}
