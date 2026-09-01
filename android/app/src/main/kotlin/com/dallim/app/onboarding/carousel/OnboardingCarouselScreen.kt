package com.dallim.app.onboarding.carousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimGradient
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.Spacing
import kotlinx.coroutines.launch

private data class OnboardingSlide(val title: String, val description: String)

private val slides = listOf(
    OnboardingSlide(
        title = "달리면서 그림을 그려요",
        description = "실제 도로 위, GPS 궤적으로 완성되는 나만의 러닝 코스",
    ),
    OnboardingSlide(
        title = "숨어있는 그림 코스를 찾아요",
        description = "우리 동네 곳곳에 그려진 코스를 발견하고 직접 달려보세요",
    ),
    OnboardingSlide(
        title = "완주한 그림을 달림북에 모아요",
        description = "달릴 때마다 늘어나는 나만의 작품집",
    ),
)

/** S-01 가치 제안 캐러셀 — docs/01-feature-spec.md 1.1: 3장 슬라이드, 스와이프/탭, 스킵 가능. */
@Composable
fun OnboardingCarouselScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.ScreenHorizontal),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.lg),
            horizontalArrangement = Arrangement.End,
        ) {
            DallimTextButton(text = "건너뛰기", onClick = onFinished)
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val slide = slides[page]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                OnboardingIllustration(
                    slideIndex = page,
                    modifier = Modifier
                        .size(180.dp)
                        .padding(bottom = Spacing.xl),
                )
                Text(
                    text = slide.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DallimColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = slide.description,
                    fontSize = 16.sp,
                    color = DallimColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.lg),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(slides.size) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) DallimColors.Primary else DallimColors.Border,
                        ),
                )
            }
        }

        val isLastPage = pagerState.currentPage == slides.lastIndex
        DallimPrimaryButton(
            text = if (isLastPage) "시작하기" else "다음",
            onClick = {
                if (isLastPage) {
                    onFinished()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier.padding(bottom = Spacing.xl),
        )
    }
}

/**
 * 슬라이드별 라인아트 일러스트 (docs/04-ui-guide.md §7: 이모지 금지, 벡터/Canvas만).
 * `RouteThumbnailView`와 같은 톤 — 심플한 실루엣 라인 — 을 따르되, 실데이터가 아닌 장식용
 * 도형이므로 core-ui의 GeoJSON 렌더러 대신 이 화면 전용 Canvas로 직접 그린다. 그라디언트는
 * 온보딩 슬라이드에 허용된 4곳 중 하나(docs/03-design-system.md 1.2).
 */
@Composable
private fun OnboardingIllustration(slideIndex: Int, modifier: Modifier = Modifier) {
    when (slideIndex) {
        0 -> RunDrawsPictureIllustration(modifier)
        1 -> DiscoverCourseIllustration(modifier)
        2 -> DallimbookCollectionIllustration(modifier)
        else -> Box(modifier)
    }
}

/** "달리면서 그림을 그려요" — 하트 모양으로 이어지는 GPS 궤적. GPS 러닝으로 그림이 만들어진다는
 * 컨셉을 가장 직관적으로 보여주는 형태(러닝 앱에서 흔한 "하트 모양 코스" 클리셰). */
@Composable
private fun RunDrawsPictureIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        fun px(x: Float) = x / 100f * size.width
        fun py(y: Float) = y / 100f * size.height

        val heart = Path().apply {
            moveTo(px(50f), py(86f))
            cubicTo(px(22f), py(64f), px(6f), py(41f), px(6f), py(26f))
            cubicTo(px(6f), py(12f), px(20f), py(6f), px(34f), py(11f))
            cubicTo(px(43f), py(14f), px(49f), py(20f), px(50f), py(26f))
            cubicTo(px(51f), py(20f), px(57f), py(14f), px(66f), py(11f))
            cubicTo(px(80f), py(6f), px(94f), py(12f), px(94f), py(26f))
            cubicTo(px(94f), py(41f), px(78f), py(64f), px(50f), py(86f))
        }

        drawPath(
            path = heart,
            brush = DallimGradient,
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // GPS trackpoint dots along the trail.
        val dots = listOf(50f to 86f, 6f to 26f, 34f to 11f, 66f to 11f, 94f to 26f)
        dots.forEach { (x, y) ->
            drawCircle(color = DallimColors.Primary, radius = 3.dp.toPx(), center = Offset(px(x), py(y)))
        }
    }
}

/** "숨어있는 그림 코스를 찾아요" — 흐린 도로망 위, 발견된 코스 한 줄기와 핀 마커. 아직
 * 발견되지 않은 코스는 옅은 회색 점으로 암시("숨어있는" 뉘앙스). */
@Composable
private fun DiscoverCourseIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        fun px(x: Float) = x / 100f * size.width
        fun py(y: Float) = y / 100f * size.height

        // Faint background road grid.
        val roadStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(DallimColors.Border, Offset(px(8f), py(20f)), Offset(px(92f), py(32f)), roadStroke.width)
        drawLine(DallimColors.Border, Offset(px(15f), py(85f)), Offset(px(85f), py(15f)), roadStroke.width)
        drawLine(DallimColors.Border, Offset(px(10f), py(60f)), Offset(px(70f), py(90f)), roadStroke.width)

        // Undiscovered courses — muted dots.
        drawCircle(DallimColors.RouteDiscovery, radius = 3.dp.toPx(), center = Offset(px(20f), py(70f)))
        drawCircle(DallimColors.RouteDiscovery, radius = 3.dp.toPx(), center = Offset(px(78f), py(60f)))

        // The discovered course — highlighted trail.
        val found = Path().apply {
            moveTo(px(18f), py(78f))
            cubicTo(px(30f), py(60f), px(35f), py(50f), px(48f), py(45f))
            cubicTo(px(58f), py(41f), px(58f), py(32f), px(66f), py(24f))
        }
        drawPath(
            path = found,
            color = DallimColors.Primary,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Discovery "ping" ring around the pin.
        drawCircle(
            color = DallimColors.Primary.copy(alpha = 0.25f),
            radius = 14.dp.toPx(),
            center = Offset(px(66f), py(20f)),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))),
        )

        // Map pin (teardrop) at the trail's end.
        val pinCenter = Offset(px(66f), py(20f))
        val pinRadius = 8.dp.toPx()
        val pin = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    center = pinCenter,
                    radius = pinRadius,
                ),
            )
            moveTo(pinCenter.x - pinRadius * 0.75f, pinCenter.y + pinRadius * 0.55f)
            lineTo(pinCenter.x, pinCenter.y + pinRadius * 2.1f)
            lineTo(pinCenter.x + pinRadius * 0.75f, pinCenter.y + pinRadius * 0.55f)
            close()
        }
        drawPath(path = pin, color = DallimColors.Primary)
        drawCircle(color = DallimColors.Background, radius = pinRadius * 0.4f, center = pinCenter)
    }
}

/** "완주한 그림을 달림북에 모아요" — 2x2 그리드에 담긴 미니 코스 썸네일들. `RouteThumbnailView`와
 * 같은 톤(PrimaryLight 배경 + Primary 실선)의 장식용 미니어처를 격자로 배치해 "컬렉션" 느낌을 준다. */
@Composable
private fun DallimbookCollectionIllustration(modifier: Modifier = Modifier) {
    val miniShapes = listOf(
        listOf(0.2f to 0.75f, 0.4f to 0.3f, 0.6f to 0.6f, 0.8f to 0.2f),
        listOf(0.2f to 0.3f, 0.35f to 0.75f, 0.5f to 0.35f, 0.65f to 0.75f, 0.8f to 0.3f),
        listOf(0.25f to 0.25f, 0.25f to 0.75f, 0.75f to 0.75f),
        listOf(0.5f to 0.2f, 0.8f to 0.5f, 0.5f to 0.8f, 0.2f to 0.5f, 0.5f to 0.2f),
    )

    Canvas(modifier = modifier) {
        val gap = size.width * 0.06f
        val pad = size.width * 0.04f
        val cell = (size.width - 2 * pad - gap) / 2f
        val corner = CornerRadius(cell * 0.22f, cell * 0.22f)

        val origins = listOf(
            Offset(pad, pad),
            Offset(pad + cell + gap, pad),
            Offset(pad, pad + cell + gap),
            Offset(pad + cell + gap, pad + cell + gap),
        )

        origins.forEachIndexed { index, origin ->
            drawRoundRect(
                color = DallimColors.PrimaryLight,
                topLeft = origin,
                size = Size(cell, cell),
                cornerRadius = corner,
            )

            val shape = miniShapes[index % miniShapes.size]
            val path = Path().apply {
                shape.forEachIndexed { i, (fx, fy) ->
                    val point = Offset(origin.x + fx * cell, origin.y + fy * cell)
                    if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            }
            drawPath(
                path = path,
                color = DallimColors.Primary,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingCarouselScreenPreview() {
    DallimTheme {
        OnboardingCarouselScreen(onFinished = {})
    }
}
