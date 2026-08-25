package com.dallim.app.onboarding.carousel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.theme.DallimColors
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

@Preview(showBackground = true)
@Composable
private fun OnboardingCarouselScreenPreview() {
    DallimTheme {
        OnboardingCarouselScreen(onFinished = {})
    }
}
