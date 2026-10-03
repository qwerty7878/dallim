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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
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
import com.dallim.app.onboarding.OnboardingArt
import com.dallim.app.onboarding.OnboardingMapArt
import com.dallim.ui.theme.DallimTypography
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
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.End,
        ) {
            DallimTextButton(text = "건너뛰기", onClick = onFinished)
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { page ->
            val slide = slides[page]
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                OnboardingIllustration(slideIndex = page)
                Text(
                    text = slide.title,
                    style = DallimTypography.Title1,
                    color = DallimColors.TextPrimary,
                    modifier = Modifier.padding(top = Spacing.xl),
                )
                Text(
                    text = slide.description,
                    style = DallimTypography.Body,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }

        // 페이지 표시 — 현재 페이지는 긴 막대(Ink), 나머지는 작은 점.
        Row(
            modifier = Modifier.padding(vertical = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(slides.size) { index ->
                val current = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(if (current) 20.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (current) DallimColors.Ink else DallimColors.Border),
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

/** 슬라이드별 이미지 — 전부 실제 지도 위의 GPS 그림(손그림 일러스트 폐지, 2026-10-04). */
@Composable
private fun OnboardingIllustration(slideIndex: Int) {
    when (slideIndex) {
        0 -> OnboardingMapArt(OnboardingArt.Heart, Modifier.aspectRatio(1f))
        1 -> OnboardingMapArt(OnboardingArt.Star, Modifier.aspectRatio(1f))
        else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            // 달림북 — 작품 4장을 2x2로 모아 둔 모습.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OnboardingMapArt(OnboardingArt.Heart, Modifier.weight(1f).aspectRatio(1f))
                OnboardingMapArt(OnboardingArt.Star, Modifier.weight(1f).aspectRatio(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OnboardingMapArt(OnboardingArt.Wave, Modifier.weight(1f).aspectRatio(1f))
                OnboardingMapArt(OnboardingArt.Loop, Modifier.weight(1f).aspectRatio(1f))
            }
        }
    }
}
